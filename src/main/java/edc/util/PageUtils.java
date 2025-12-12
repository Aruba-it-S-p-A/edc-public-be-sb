package edc.util;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@UtilityClass
@Slf4j
public class PageUtils {

  private static final String CREATED_AT_COLUMN = "id";

  public static Pageable getPageable(
      int limit, int page, List<String> orderBy, Sort.Direction order) {
    Sort orders = checkOrder(orderBy, order);
    return PageRequest.of(page, limit, orders);
  }

  private static Sort checkOrder(List<String> orderBy, Sort.Direction order) {
    if (!orderBy.isEmpty()) {
      final List<Sort.Order> orders = new ArrayList<>();
      orderBy.forEach(
          s -> orders.add(order.isAscending() ? Sort.Order.asc(s) : Sort.Order.desc(s)));
      if (!orders.isEmpty()) {
        //return Sort.by(orders).and(Sort.by(CREATED_AT_COLUMN).descending());
        return Sort.by(orders);
      }
    }
    return Sort.unsorted();
  }

  public static HttpHeaders setPaginationHeaders(Page<?> page) {
    final HttpHeaders headers = new HttpHeaders();
    headers.add("x-page", String.valueOf(page.getPageable().getPageNumber()));
    headers.add("x-total", String.valueOf(page.getTotalElements()));
    headers.add("x-count", String.valueOf(page.getNumberOfElements()));
    headers.add("x-limit", String.valueOf(page.getPageable().getPageSize()));
    return headers;
  }

  public static <T> Specification<T> getSpecification(
      Map<String, String> filters, String q, String... searchFields) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      filters.forEach(
          (key, value) -> {
            if (value != null
                && !value.isEmpty()
                && !key.equals("startDate")
                && !key.equals("endDate")) {
              predicates.add(cb.equal(root.get(key), value));
            }
          });

      if (q != null && !q.isEmpty() && searchFields != null && searchFields.length > 0) {
        List<Predicate> searchPredicates = new ArrayList<>();
        for (String field : searchFields) {
          searchPredicates.add(cb.like(cb.lower(root.get(field)), "%" + q.toLowerCase() + "%"));
        }
        predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
      }

      Specification<T> temporalSpec = TemporalFilterUtils.withTemporalFilters(filters, "createdAt");
      predicates.add(temporalSpec.toPredicate(root, query, cb));

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
