package dev.minicard.processor.meta.web.dto;

import dev.minicard.processor.common.reference.ReferenceData;
import java.util.List;

public record ReferenceDataResponse(
    List<ReferenceData.ResponseCodeItem> responseCodes,
    List<ReferenceData.DeclineReasonItem> declineReasons,
    List<ReferenceData.MccItem> mccs,
    List<ReferenceData.CurrencyItem> currencies,
    List<String> disputeReasons) {}
