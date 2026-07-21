package com.agricore.assistant.infrastructure.tool.farm;

import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import com.agricore.assistant.application.port.ToolCollectionException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class FarmToolEvidenceProjector {

    ToolEvidenceSnapshot project(
            UUID requestedFarmId,
            FarmToolResponseDecoder.FarmDetails farm,
            FarmToolResponseDecoder.PlotPage plots,
            int maximumPlots
    ) {
        requireValidFarm(requestedFarmId, farm);
        requireValidPage(requestedFarmId, plots, maximumPlots);
        List<ToolFact> facts = new ArrayList<>();
        facts.add(new ToolFact("FARM-1", ToolSource.FARM, farmFields(farm, plots)));
        for (int index = 0; index < plots.content().size(); index++) {
            facts.add(new ToolFact(
                    "PLOT-" + (index + 1),
                    ToolSource.PLOT,
                    plotFields(plots.content().get(index))
            ));
        }
        return new ToolEvidenceSnapshot(facts);
    }

    private static Map<String, String> farmFields(
            FarmToolResponseDecoder.FarmDetails farm,
            FarmToolResponseDecoder.PlotPage plots
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("code", farm.code());
        fields.put("name", farm.name());
        fields.put("status", farm.status());
        putDecimal(fields, "totalAreaHa", farm.totalAreaHa());
        fields.put("plotCount", Long.toString(plots.totalElements()));
        fields.put("plotFactsIncluded", Integer.toString(plots.content().size()));
        return fields;
    }

    private static Map<String, String> plotFields(FarmToolResponseDecoder.PlotDetails plot) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("code", plot.code());
        fields.put("name", plot.name());
        fields.put("status", plot.status());
        putDecimal(fields, "areaInHectares", plot.areaInHectares());
        if (plot.soilType() != null && !plot.soilType().isBlank()) {
            fields.put("soilType", plot.soilType());
        }
        return fields;
    }

    private static void requireValidFarm(UUID requestedFarmId, FarmToolResponseDecoder.FarmDetails farm) {
        if (farm == null || !requestedFarmId.equals(farm.id()) || isBlank(farm.code())
                || isBlank(farm.name()) || isBlank(farm.status()) || farm.version() == null
                || farm.createdAt() == null || farm.updatedAt() == null) {
            throw ToolCollectionException.responseInvalid();
        }
        requireNonNegative(farm.totalAreaHa());
    }

    private static void requireValidPage(
            UUID farmId,
            FarmToolResponseDecoder.PlotPage page,
            int maximumPlots
    ) {
        if (page == null || page.content() == null || page.page() == null || page.page() != 0
                || page.size() == null || page.size() < 1 || page.size() > maximumPlots
                || page.totalElements() == null || page.totalElements() < page.content().size()
                || page.totalPages() == null || page.totalPages() < 0 || !Boolean.TRUE.equals(page.first())
                || page.last() == null || page.content().size() > maximumPlots) {
            throw ToolCollectionException.responseInvalid();
        }
        page.content().forEach(plot -> requireValidPlot(farmId, plot));
    }

    private static void requireValidPlot(UUID farmId, FarmToolResponseDecoder.PlotDetails plot) {
        if (plot == null || plot.id() == null || !farmId.equals(plot.farmId()) || isBlank(plot.code())
                || isBlank(plot.name()) || isBlank(plot.status()) || plot.version() == null
                || plot.createdAt() == null || plot.updatedAt() == null) {
            throw ToolCollectionException.responseInvalid();
        }
        requireNonNegative(plot.areaInHectares());
    }

    private static void requireNonNegative(BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw ToolCollectionException.responseInvalid();
        }
    }

    private static void putDecimal(Map<String, String> fields, String name, BigDecimal value) {
        if (value != null) {
            fields.put(name, value.stripTrailingZeros().toPlainString());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
