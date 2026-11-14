package com.flyscanner.search.domain;

import java.util.List;

public record SupplierSearchResult(List<SupplierOffer> offers) {
    public SupplierSearchResult {
        offers = List.copyOf(offers);
    }
}
