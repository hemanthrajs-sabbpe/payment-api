package com.example.payment_api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentSplitServiceTest {

    @Mock
    private PaymentSplitRepository splitRepository;

    @Mock
    private PaymentSplitAllocationRepository allocationRepository;

    @Mock
    private BusinessCategoryRepository businessCategoryRepository;

    @Mock
    private BusinessMemberRepository businessMemberRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private PaymentSplitService paymentSplitService;

    private BusinessCategory activeCategory;
    private BusinessMember activeOwnerMember;

    @BeforeEach
    void setUp() {
        activeCategory = new BusinessCategory();
        activeCategory.setStatus("ACTIVE");

        activeOwnerMember = new BusinessMember();
        activeOwnerMember.setOrganizationId("ORG-LOCAL-TEST-001");
        activeOwnerMember.setUserId(1L);
        activeOwnerMember.setMemberRole("OWNER");
        activeOwnerMember.setMembershipStatus("ACTIVE");
    }

    @Test
    @DisplayName("Personal split creation should succeed without organization membership")
    void testCreatePersonalSplitSuccess() {
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(splitRepository.save(any(PaymentSplit.class))).thenAnswer(invocation -> {
            PaymentSplit split = invocation.getArgument(0);
            return split;
        });

        PaymentSplit split = paymentSplitService.createSplit(
                "SECTOR_SPLIT",
                new BigDecimal("100.00"),
                "INR",
                "INDIVIDUAL",
                BigDecimal.ZERO,
                "PERSONAL",
                null,
                null
        );

        assertNotNull(split);
        assertEquals("PERSONAL", split.getSplitScope());
        assertNull(split.getOrganizationId());
        assertNull(split.getBusinessCategoryId());
        verify(businessMemberRepository, never()).findByOrganizationIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("Business split creation should succeed when user is active OWNER of organization")
    void testCreateBusinessSplitSuccess() {
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(businessMemberRepository.findByOrganizationIdAndUserId("ORG-LOCAL-TEST-001", 1L))
                .thenReturn(Optional.of(activeOwnerMember));
        when(businessCategoryRepository.findById(1L)).thenReturn(Optional.of(activeCategory));
        when(splitRepository.save(any(PaymentSplit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentSplit split = paymentSplitService.createSplit(
                "RESTAURANT",
                new BigDecimal("200.00"),
                "INR",
                "INDIVIDUAL",
                BigDecimal.ZERO,
                "BUSINESS",
                "ORG-LOCAL-TEST-001",
                1L
        );

        assertNotNull(split);
        assertEquals("BUSINESS", split.getSplitScope());
        assertEquals("ORG-LOCAL-TEST-001", split.getOrganizationId());
        assertEquals(1L, split.getBusinessCategoryId());
        verify(businessMemberRepository).findByOrganizationIdAndUserId("ORG-LOCAL-TEST-001", 1L);
    }

    @Test
    @DisplayName("Business split creation should throw SecurityException when user is not a member of organization")
    void testCreateBusinessSplitThrowsSecurityExceptionForNonMember() {
        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(businessMemberRepository.findByOrganizationIdAndUserId("ORG-UNAUTHORIZED", 1L))
                .thenReturn(Optional.empty());

        SecurityException ex = assertThrows(SecurityException.class, () ->
                paymentSplitService.createSplit(
                        "RESTAURANT",
                        new BigDecimal("200.00"),
                        "INR",
                        "INDIVIDUAL",
                        BigDecimal.ZERO,
                        "BUSINESS",
                        "ORG-UNAUTHORIZED",
                        1L
                )
        );

        assertEquals("You are not a member of this organization", ex.getMessage());
        verify(splitRepository, never()).save(any(PaymentSplit.class));
    }
}
