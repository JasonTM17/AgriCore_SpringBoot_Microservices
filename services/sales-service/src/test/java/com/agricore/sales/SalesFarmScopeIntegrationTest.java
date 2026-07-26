package com.agricore.sales;

import com.agricore.farmaccess.FarmAccessClient;
import com.agricore.farmaccess.FarmAccessException;
import com.agricore.sales.domain.model.OrderStatus;
import com.agricore.sales.infrastructure.client.InventoryClient;
import com.agricore.sales.infrastructure.persistence.CustomerJpaRepository;
import com.agricore.sales.infrastructure.persistence.SalesOrderJpaRepository;
import com.agricore.sales.infrastructure.persistence.entity.CustomerEntity;
import com.agricore.sales.infrastructure.persistence.entity.SalesOrderEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesFarmScopeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerJpaRepository customerRepository;
    @Autowired
    private SalesOrderJpaRepository orderRepository;
    @MockitoBean
    private InventoryClient inventoryClient;
    @MockitoBean
    private FarmAccessClient farmAccessClient;

    @Test
    void deniedFarmCannotCreateCustomer() throws Exception {
        UUID farmId = UUID.randomUUID();
        String customerCode = "DENIED-" + System.nanoTime();
        long customerCount = customerRepository.count();
        denyFarm(farmId);

        mockMvc.perform(post("/api/v1/sales/customers")
                        .header("X-Dev-User", "cross-farm-user")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .header("X-Dev-Permissions", "SALES_WRITE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "farmId":"%s",
                                  "code":"%s",
                                  "name":"Denied customer"
                                }
                                """.formatted(farmId, customerCode)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"));

        assertThat(customerRepository.count()).isEqualTo(customerCount);
        assertThat(customerRepository.existsByCodeIgnoreCase(customerCode)).isFalse();
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void orderCannotReferenceCustomerFromAnotherFarm() throws Exception {
        UUID customerFarmId = UUID.randomUUID();
        UUID requestedFarmId = UUID.randomUUID();
        CustomerEntity customer = persistCustomer(customerFarmId);
        long orderCount = orderRepository.count();

        mockMvc.perform(post("/api/v1/sales/orders")
                        .header("X-Dev-User", "sales-user")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .header("X-Dev-Permissions", "SALES_WRITE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNumber":"CROSS-FARM-%s",
                                  "farmId":"%s",
                                  "customerId":"%s",
                                  "inventoryItemId":"%s",
                                  "quantity":5.000
                                }
                                """.formatted(
                                System.nanoTime(),
                                requestedFarmId,
                                customer.getId(),
                                UUID.randomUUID()
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));

        assertThat(orderRepository.count()).isEqualTo(orderCount);
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void deniedFarmCannotReadExistingOrder() throws Exception {
        UUID farmId = UUID.randomUUID();
        CustomerEntity customer = persistCustomer(farmId);
        SalesOrderEntity order = persistOrder(farmId, customer.getId());
        denyFarm(farmId);

        mockMvc.perform(get("/api/v1/sales/orders/" + order.getId())
                        .header("X-Dev-User", "cross-farm-user")
                        .header("X-Dev-Roles", "SALES_STAFF")
                        .header("X-Dev-Permissions", "SALES_READ"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FARM_ACCESS_DENIED"))
                .andExpect(jsonPath("$.path").value("/api/v1/sales/orders/" + order.getId()));

        verifyNoInteractions(inventoryClient);
    }

    private void denyFarm(UUID farmId) {
        doThrow(new FarmAccessException("FARM_ACCESS_DENIED", "Farm access denied", 403))
                .when(farmAccessClient)
                .requireFarm(farmId);
    }

    private CustomerEntity persistCustomer(UUID farmId) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setFarmId(farmId);
        customer.setCode("C-SCOPE-" + System.nanoTime());
        customer.setName("Scoped customer");
        customer.setCreatedAt(Instant.now());
        return customerRepository.saveAndFlush(customer);
    }

    private SalesOrderEntity persistOrder(UUID farmId, UUID customerId) {
        Instant now = Instant.now();
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("SO-SCOPE-" + System.nanoTime());
        order.setFarmId(farmId);
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        order.setInventoryItemId(UUID.randomUUID());
        order.setQuantity(new BigDecimal("5.000"));
        order.setCorrelationId(UUID.randomUUID());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return orderRepository.saveAndFlush(order);
    }
}
