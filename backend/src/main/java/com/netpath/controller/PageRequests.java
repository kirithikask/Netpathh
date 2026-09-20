package com.netpath.controller;

import com.netpath.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Collection;

/**
 * Turns raw {@code page} / {@code size} / {@code sort} query parameters into a page request.
 *
 * <p>Unbounded parameters are a client error, not a server error: {@code size=0} would otherwise
 * raise an {@link IllegalArgumentException} from Spring Data and surface as a 500, and a huge
 * {@code size} would silently stream the whole table. Both are rejected here so every list endpoint
 * answers with the same 400.
 */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }

    /**
     * Builds a sort only for properties the caller may name. A client-supplied property is otherwise
     * passed straight into the query, where an unknown or deleted field fails at the database layer
     * as a 500.
     */
    public static Sort sort(String property, String direction, Collection<String> allowedProperties) {
        if (!allowedProperties.contains(property)) {
            throw new BadRequestException(String.format(
                    "Cannot sort by '%s'; sortable properties are %s",
                    property, allowedProperties.stream().sorted().toList()));
        }

        return "asc".equalsIgnoreCase(direction)
                ? Sort.by(property).ascending()
                : Sort.by(property).descending();
    }

    public static PageRequest of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BadRequestException("'page' must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("'size' must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }
}
