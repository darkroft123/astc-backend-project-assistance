package com.smms.assistance.domain.model;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Page<T> {
    private List<T> items;
    private long totalItems;
    private int page;
    private int size;
}
