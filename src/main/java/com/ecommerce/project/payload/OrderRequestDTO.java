package com.ecommerce.project.payload;

import com.ecommerce.project.model.Address;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDTO {
    private Long addressId;
    private String pgName;
    private String pgPaymentId;
    private String pgStatus;
    private String pgResponseMessage;
}
