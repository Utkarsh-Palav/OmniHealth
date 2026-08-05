package com.omnihealth.common.response;

import java.util.List;

public record PageResponse<T>(

        List<T> content,

        PaginationMeta pagination

) {
}
