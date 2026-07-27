package com.agricore.assistant.infrastructure.rag;

import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.application.port.KnowledgeRetriever;
import com.agricore.assistant.application.port.ToolCollectionException;
import com.agricore.assistant.infrastructure.configuration.AssistantRagProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

@Component
public class JdbcKnowledgeRetriever implements KnowledgeRetriever {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_TERM = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "do", "for", "how", "i", "is", "of", "or", "the", "to",
            "va", "la", "cua", "cho", "mot", "nhung", "voi", "duoc"
    );

    private final JdbcTemplate jdbc;
    private final AssistantRagProperties properties;

    public JdbcKnowledgeRetriever(JdbcTemplate jdbc, AssistantRagProperties properties) {
        this.jdbc = requireNonNull(jdbc, "jdbc is required");
        this.properties = requireNonNull(properties, "RAG properties are required");
        if (properties.isEnabled()) {
            properties.validatedMaxResults();
            properties.validatedMaxQueryTerms();
            properties.validatedMaxExcerptCharacters();
            properties.validatedQueryTimeoutSeconds();
        }
    }

    @Override
    public List<ToolFact> retrieve(String query) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        List<String> terms = normalizedTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(terms.size(), "?"));
        String sql = """
                SELECT c.source_key, c.title, c.content, c.source_uri, SUM(t.weight) AS relevance
                FROM assistant_knowledge_chunks c
                JOIN assistant_knowledge_terms t ON t.chunk_id = c.id
                WHERE c.enabled = TRUE AND t.term IN (%s)
                GROUP BY c.source_key, c.title, c.content, c.source_uri
                ORDER BY relevance DESC, c.source_key ASC
                LIMIT ?
                """.formatted(placeholders);
        try {
            return jdbc.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                int parameter = 1;
                for (String term : terms) {
                    statement.setString(parameter++, term);
                }
                statement.setInt(parameter, properties.validatedMaxResults());
                statement.setQueryTimeout(properties.validatedQueryTimeoutSeconds());
                return statement;
            }, (resultSet, rowNumber) -> toFact(
                    rowNumber,
                    resultSet.getString("title"),
                    resultSet.getString("content"),
                    resultSet.getString("source_uri"),
                    resultSet.getInt("relevance")
            ));
        } catch (DataAccessException exception) {
            throw ToolCollectionException.ragUnavailable();
        }
    }

    private List<String> normalizedTerms(String query) {
        String normalized = Normalizer.normalize(query == null ? "" : query, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = normalized.replace('đ', 'd');
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String term : NON_TERM.split(normalized)) {
            if (term.length() >= 2 && !STOP_WORDS.contains(term)) {
                terms.add(term);
            }
            if (terms.size() == properties.validatedMaxQueryTerms()) {
                break;
            }
        }
        return List.copyOf(terms);
    }

    private ToolFact toFact(
            int rowNumber,
            String title,
            String content,
            String sourceUri,
            int relevance
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("title", title);
        fields.put("excerpt", excerpt(content, properties.validatedMaxExcerptCharacters()));
        fields.put("sourceUri", sourceUri);
        fields.put("relevance", Integer.toString(relevance));
        return new ToolFact("KB-" + (rowNumber + 1), ToolSource.KNOWLEDGE, fields);
    }

    private static String excerpt(String content, int maximumCharacters) {
        String normalized = content == null ? "" : content.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= maximumCharacters) {
            return normalized;
        }
        int boundary = normalized.lastIndexOf(' ', maximumCharacters - 1);
        int end = boundary >= 80 ? boundary : maximumCharacters - 1;
        return normalized.substring(0, end).stripTrailing() + "…";
    }
}
