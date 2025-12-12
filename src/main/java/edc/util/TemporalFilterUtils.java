package edc.util;

import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Map;

@UtilityClass
public class TemporalFilterUtils {

  private static final String START_DATE_COLUMN = "startDate";
  private static final String END_DATE_COLUMN = "endDate";

  public static void addTemporalFilters(
      final Map<String, String> filters, final LocalDate startDate, final LocalDate endDate) {
    if (startDate != null) {
      filters.put(START_DATE_COLUMN, startDate.toString());
    }
    if (endDate != null) {
      filters.put(END_DATE_COLUMN, endDate.toString());
    }
  }

  public static <T> Specification<T> withTemporalFilters(
      final Map<String, String> filters, final String createdAtFieldName) {
    return (root, query, cb) -> {
      Predicate predicate = cb.conjunction();

      if (filters.containsKey(START_DATE_COLUMN)) {
        LocalDate startDate = LocalDate.parse(filters.get(START_DATE_COLUMN));
        predicate =
            cb.and(
                predicate,
                cb.greaterThanOrEqualTo(root.get(createdAtFieldName), startDate.atStartOfDay()));
      }

      if (filters.containsKey(END_DATE_COLUMN)) {
        LocalDate endDate = LocalDate.parse(filters.get(END_DATE_COLUMN));
        predicate =
            cb.and(
                predicate,
                cb.lessThanOrEqualTo(
                    root.get(createdAtFieldName), endDate.plusDays(1).atStartOfDay()));
      }

      return predicate;
    };
  }
}
