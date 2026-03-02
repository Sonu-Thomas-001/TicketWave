package com.ticketwave.refund.application;

import com.ticketwave.refund.domain.RefundLedger;
import com.ticketwave.refund.infrastructure.RefundLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RefundLedgerService.
 * Tests ledger entry creation and reconciliation tracking.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefundLedgerService Tests")
class RefundLedgerServiceTest {

    @Mock
    private RefundLedgerRepository refundLedgerRepository;

    @InjectMocks
    private RefundLedgerService refundLedgerService;

    private UUID refundId;
    private UUID bookingId;
    private RefundLedger mockLedgerEntry;

    @BeforeEach
    void setUp() {
        refundId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        mockLedgerEntry = RefundLedger.builder()
                .id(UUID.randomUUID())
                .ledgerEntryId("LED-ABC123")
                .refundId(refundId)
                .bookingId(bookingId)
                .entryType("REFUND_AMOUNT")
                .description("Original refundable amount")
                .amount(BigDecimal.valueOf(1000))
                .balanceAfter(BigDecimal.valueOf(1000))
                .build();
    }

    @Test
    @DisplayName("Should create refund amount entry")
    void shouldCreateRefundAmountEntry() {
        // Given
        RefundLedger.RefundLedgerBuilder builder = RefundLedger.builder()
                .refundId(refundId)
                .bookingId(bookingId)
                .entryType("REFUND_AMOUNT")
                .description("Original refundable amount")
                .amount(BigDecimal.valueOf(1000))
                .balanceAfter(BigDecimal.valueOf(1000));

        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        RefundLedger result = refundLedgerService.createRefundAmountEntry(
                refundId, bookingId, BigDecimal.valueOf(1000)
        );

        // Then
        assertNotNull(result);
        assertEquals("REFUND_AMOUNT", result.getEntryType());
        assertEquals(BigDecimal.valueOf(1000), result.getAmount());
        verify(refundLedgerRepository).save(any(RefundLedger.class));
    }

    @Test
    @DisplayName("Should create policy deduction entry")
    void shouldCreatePolicyDeductionEntry() {
        // Given
        BigDecimal balanceBefore = BigDecimal.valueOf(1000);
        BigDecimal deduction = BigDecimal.valueOf(100);

        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        RefundLedger result = refundLedgerService.createPolicyDeductionEntry(
                refundId, bookingId, deduction, balanceBefore, "Cancellation within 24 hours"
        );

        // Then
        assertNotNull(result);
        assertEquals("POLICY_DEDUCTION", result.getEntryType());
        assertEquals(deduction.negate(), result.getAmount());
        assertEquals(BigDecimal.valueOf(900), result.getBalanceAfter());
        verify(refundLedgerRepository).save(any(RefundLedger.class));
    }

    @Test
    @DisplayName("Should create processing fee entry")
    void shouldCreateProcessingFeeEntry() {
        // Given
        BigDecimal balanceBefore = BigDecimal.valueOf(900);
        BigDecimal fee = BigDecimal.valueOf(22.5);

        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        RefundLedger result = refundLedgerService.createProcessingFeeEntry(
                refundId, bookingId, fee, balanceBefore
        );

        // Then
        assertNotNull(result);
        assertEquals("PROCESSING_FEE", result.getEntryType());
        assertEquals(fee.negate(), result.getAmount());
        assertEquals(BigDecimal.valueOf(877.5), result.getBalanceAfter());
        verify(refundLedgerRepository).save(any(RefundLedger.class));
    }

    @Test
    @DisplayName("Should create final amount entry")
    void shouldCreateFinalAmountEntry() {
        // Given
        BigDecimal finalAmount = BigDecimal.valueOf(877.5);

        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        RefundLedger result = refundLedgerService.createFinalAmountEntry(
                refundId, bookingId, finalAmount
        );

        // Then
        assertNotNull(result);
        assertEquals("FINAL_AMOUNT", result.getEntryType());
        assertEquals(finalAmount, result.getAmount());
        verify(refundLedgerRepository).save(any(RefundLedger.class));
    }

    @Test
    @DisplayName("Should create admin adjustment entry")
    void shouldCreateAdjustmentEntry() {
        // Given
        BigDecimal balanceBefore = BigDecimal.valueOf(877.5);
        BigDecimal adjustment = BigDecimal.valueOf(50); // Increase by 50

        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        RefundLedger result = refundLedgerService.createAdjustmentEntry(
                refundId, bookingId, adjustment, balanceBefore,
                "Goodwill gesture", "ADMIN-001"
        );

        // Then
        assertNotNull(result);
        assertEquals("ADJUSTMENT", result.getEntryType());
        assertEquals(adjustment, result.getAmount());
        assertEquals(BigDecimal.valueOf(927.5), result.getBalanceAfter());
        assertEquals("Goodwill gesture", result.getAdjustmentReason());
        assertEquals("ADMIN-001", result.getAdjustedByAdmin());
        assertNotNull(result.getAdjustedAt());
        verify(refundLedgerRepository).save(any(RefundLedger.class));
    }

    @Test
    @DisplayName("Should retrieve all ledger entries for a refund")
    void shouldGetLedgerEntriesForRefund() {
        // Given
        List<RefundLedger> entries = List.of(mockLedgerEntry);
        when(refundLedgerRepository.findByRefundIdOrderByCreatedAt(refundId))
                .thenReturn(entries);

        // When
        List<RefundLedger> result = refundLedgerService.getLedgerEntriesForRefund(refundId);

        // Then
        assertEquals(1, result.size());
        assertEquals("LED-ABC123", result.get(0).getLedgerEntryId());
    }

    @Test
    @DisplayName("Should retrieve all ledger entries for a booking")
    void shouldGetLedgerEntriesForBooking() {
        // Given
        List<RefundLedger> entries = List.of(mockLedgerEntry);
        when(refundLedgerRepository.findByBookingIdOrderByCreatedAt(bookingId))
                .thenReturn(entries);

        // When
        List<RefundLedger> result = refundLedgerService.getLedgerEntriesForBooking(bookingId);

        // Then
        assertEquals(1, result.size());
        assertEquals(bookingId, result.get(0).getBookingId());
    }

    @Test
    @DisplayName("Should get total refund for a booking")
    void shouldGetTotalRefundForBooking() {
        // Given
        BigDecimal totalRefund = BigDecimal.valueOf(877.5);
        when(refundLedgerRepository.getTotalRefundForBooking(bookingId))
                .thenReturn(totalRefund);

        // When
        BigDecimal result = refundLedgerService.getTotalRefundForBooking(bookingId);

        // Then
        assertEquals(totalRefund, result);
    }

    @Test
    @DisplayName("Should return zero when no refund entries found")
    void shouldReturnZeroWhenNoRefunds() {
        // Given
        when(refundLedgerRepository.getTotalRefundForBooking(bookingId))
                .thenReturn(null);

        // When
        BigDecimal result = refundLedgerService.getTotalRefundForBooking(bookingId);

        // Then
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    @DisplayName("Should get adjustments made by specific admin")
    void shouldGetAdjustmentsByAdmin() {
        // Given
        RefundLedger adjustment = RefundLedger.builder()
                .entryType("ADJUSTMENT")
                .adjustedByAdmin("ADMIN-001")
                .build();

        List<RefundLedger> adjustments = List.of(adjustment);
        when(refundLedgerRepository.findAdjustmentsByAdmin("ADMIN-001"))
                .thenReturn(adjustments);

        // When
        List<RefundLedger> result = refundLedgerService.getAdjustmentsByAdmin("ADMIN-001");

        // Then
        assertEquals(1, result.size());
        assertEquals("ADMIN-001", result.get(0).getAdjustedByAdmin());
    }

    @Test
    @DisplayName("Should count ledger entries by type")
    void shouldCountEntriesByType() {
        // Given
        when(refundLedgerRepository.countByEntryType("REFUND_AMOUNT"))
                .thenReturn(100L);

        // When
        long result = refundLedgerService.countEntriesByType("REFUND_AMOUNT");

        // Then
        assertEquals(100L, result);
    }

    @Test
    @DisplayName("Should create complete ledger entry breakdown for refund")
    void shouldCreateCompleteBreakdown() {
        // Given: Simulate complete refund breakdown
        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When: Create all entries (original -> deduction -> fee -> final)
        RefundLedger entry1 = refundLedgerService.createRefundAmountEntry(
                refundId, bookingId, BigDecimal.valueOf(1000)
        );

        RefundLedger entry2 = refundLedgerService.createPolicyDeductionEntry(
                refundId, bookingId, BigDecimal.valueOf(100), 
                BigDecimal.valueOf(1000), "Policy deduction"
        );

        RefundLedger entry3 = refundLedgerService.createProcessingFeeEntry(
                refundId, bookingId, BigDecimal.valueOf(22.5),
                BigDecimal.valueOf(900)
        );

        RefundLedger entry4 = refundLedgerService.createFinalAmountEntry(
                refundId, bookingId, BigDecimal.valueOf(877.5)
        );

        // Then: Verify sequence
        assertEquals(BigDecimal.valueOf(1000), entry1.getAmount());
        assertEquals(BigDecimal.valueOf(-100), entry2.getAmount());
        assertEquals(BigDecimal.valueOf(-22.5), entry3.getAmount());
        assertEquals(BigDecimal.valueOf(877.5), entry4.getAmount());
        
        assertEquals(BigDecimal.valueOf(1000), entry1.getBalanceAfter());
        assertEquals(BigDecimal.valueOf(900), entry2.getBalanceAfter());
        assertEquals(BigDecimal.valueOf(877.5), entry3.getBalanceAfter());
        
        verify(refundLedgerRepository, times(4)).save(any(RefundLedger.class));
    }

    @Test
    @DisplayName("Should generate unique ledger entry IDs")
    void shouldGenerateUniqueEntryIds() {
        // Given
        when(refundLedgerRepository.save(any(RefundLedger.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // When: Create multiple entries
        RefundLedger entry1 = refundLedgerService.createRefundAmountEntry(
                refundId, bookingId, BigDecimal.valueOf(1000)
        );

        RefundLedger entry2 = refundLedgerService.createRefundAmountEntry(
                refundId, bookingId, BigDecimal.valueOf(500)
        );

        // Then: IDs should be different
        assertNotEquals(entry1.getLedgerEntryId(), entry2.getLedgerEntryId());
        assertTrue(entry1.getLedgerEntryId().startsWith("LED-"));
        assertTrue(entry2.getLedgerEntryId().startsWith("LED-"));
    }
}
