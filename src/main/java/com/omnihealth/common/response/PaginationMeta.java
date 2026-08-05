package com.omnihealth.common.response;

public record PaginationMeta(

        int page, // Current page number
        int size, // Number of record requested per page
        long totalElements, // Total number of records available
        int totalPages, // Total number of pages
        boolean first, // Indicates weather this is the first page
        boolean last, // Indicates weather this is the last page
        boolean hasNext,
        boolean hasPrevious

) {
}
