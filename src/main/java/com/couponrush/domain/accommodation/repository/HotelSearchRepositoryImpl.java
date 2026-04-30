package com.couponrush.domain.accommodation.repository;

import com.couponrush.domain.accommodation.dto.HotelCardDto;
import com.couponrush.domain.accommodation.dto.SearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class HotelSearchRepositoryImpl implements HotelSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<HotelCardDto> findHotels(SearchRequest req) {
        SqlParameterSource params = buildParams(req);
        String orderBy = orderClause(req.sort());

        String sql = """
            SELECT h.id, h.name, h.region, h.city, h.thumbnail_url,
                   h.rating_avg, h.rating_count,
                   MIN(room_avg.avg_price) AS from_price
            FROM hotels h
            JOIN rooms r ON r.hotel_id = h.id AND r.max_coverage >= :guests
            JOIN LATERAL (
                SELECT AVG(ri.price) AS avg_price, COUNT(*) AS cnt
                FROM room_inventories ri
                WHERE ri.room_id = r.id
                  AND ri.date >= :checkIn AND ri.date < :checkOut
                  AND ri.used_quantity < ri.max_quantity
            ) room_avg ON room_avg.cnt = DATEDIFF(:checkOut, :checkIn)
            WHERE h.region = :region
              AND h.rating_avg >= :minRating
            GROUP BY h.id
            HAVING MIN(room_avg.avg_price) BETWEEN :minPrice AND :maxPrice
            """ + orderBy + """

            LIMIT :limit OFFSET :offset
            """;

        return jdbc.query(sql, params, (rs, rn) -> new HotelCardDto(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("region"),
            rs.getString("city"),
            rs.getString("thumbnail_url"),
            rs.getInt("from_price"),
            rs.getDouble("rating_avg"),
            rs.getInt("rating_count")
        ));
    }

    @Override
    public long countHotels(SearchRequest req) {
        SqlParameterSource params = buildParams(req);
        String sql = """
            SELECT COUNT(*) FROM (
                SELECT h.id
                FROM hotels h
                JOIN rooms r ON r.hotel_id = h.id AND r.max_coverage >= :guests
                JOIN LATERAL (
                    SELECT AVG(ri.price) AS avg_price, COUNT(*) AS cnt
                    FROM room_inventories ri
                    WHERE ri.room_id = r.id
                      AND ri.date >= :checkIn AND ri.date < :checkOut
                      AND ri.used_quantity < ri.max_quantity
                ) room_avg ON room_avg.cnt = DATEDIFF(:checkOut, :checkIn)
                WHERE h.region = :region
                  AND h.rating_avg >= :minRating
                GROUP BY h.id
                HAVING MIN(room_avg.avg_price) BETWEEN :minPrice AND :maxPrice
            ) AS t
            """;
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    private SqlParameterSource buildParams(SearchRequest req) {
        return new MapSqlParameterSource()
            .addValue("region", req.region())
            .addValue("checkIn", req.checkIn())
            .addValue("checkOut", req.checkOut())
            .addValue("guests", req.guests())
            .addValue("minPrice", req.effectiveMinPrice())
            .addValue("maxPrice", req.effectiveMaxPrice())
            .addValue("minRating", req.effectiveMinRating())
            .addValue("limit", req.size())
            .addValue("offset", req.offset());
    }

    private String orderClause(String sort) {
        return switch (sort) {
            case "price_asc"  -> "ORDER BY MIN(room_avg.avg_price) ASC";
            case "price_desc" -> "ORDER BY MIN(room_avg.avg_price) DESC";
            case "rating"     -> "ORDER BY h.rating_avg DESC";
            case "popular"    -> "ORDER BY h.rating_count DESC";
            default -> throw new IllegalStateException("invalid sort: " + sort);
        };
    }
}
