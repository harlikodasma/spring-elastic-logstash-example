package springelasticgenie.controller;

import org.jooq.*;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import springelasticgenie.dto.ResultPage;
import springelasticgenie.model.Movie;
import springelasticgenie.repository.MovieRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.micrometer.core.instrument.util.StringUtils.isNotBlank;
import static org.jooq.impl.DSL.*;

@Controller
@RequestMapping(path="/movie")
public class MovieController {
    @Autowired
    private MovieRepository movieRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Value("${OMDB_API_KEY}")
    private String omdbKey;

    @PostMapping(path="/add")
    public ResponseEntity<?> addNewMovie(@RequestBody Map<String, ?> movieSearchParams) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");

        String omdbURL = String.format("http://www.omdbapi.com/?apikey=%s&s=%s",omdbKey, movieSearchParams.get("search"));
        RestTemplate omdbTemplate = new RestTemplate();
        Map<String, ArrayList<Map<String, String>>> omdbTemplateResult = omdbTemplate.getForObject(omdbURL, Map.class);

        try {
            ArrayList<Map<String, String>> omdbMovies = omdbTemplateResult.get("Search");
            for (Map<String, String> omdbMovie : omdbMovies) {
                Movie movie = new Movie();
                movie.setTitle(omdbMovie.get("Title"));
                movie.setImdbId(omdbMovie.get("imdbID"));
                movie.setPoster(omdbMovie.get("Poster"));
                movie.setYear(omdbMovie.get("Year"));
                movie.setType(omdbMovie.get("Type"));
                movieRepository.save(movie);
            }
            return new ResponseEntity<>("Success", headers, HttpStatus.CREATED);
        } catch(Exception e) {
            return new ResponseEntity<>("Error", headers, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public @ResponseBody ResultPage getMovieByParams(
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam String poster,
            @RequestParam String imdb_id,
            @RequestParam String title,
            @RequestParam String type
    ) {
        List<Condition> conditions = new ArrayList<>();

        conditions.add(field("poster").like("%" + poster + "%"));
        conditions.add(field("imdb_id").like("%" + imdb_id + "%"));
        conditions.add(field("title").like("%" + title + "%"));
        conditions.add(field("type").like("%" + type + "%"));

        org.jooq.Query query = selectFrom("movie")
                .where(conditions)
                .orderBy(field("poster"))
                .limit(size)
                .offset(offset)
                ;

        org.jooq.Query countQuery = selectCount()
                .from("movie")
                .where(conditions)
                ;

        javax.persistence.Query result = entityManager.createNativeQuery(query.getSQL());
        javax.persistence.Query countResult = entityManager.createNativeQuery(countQuery.getSQL());

        List<Object> values = query.getBindValues();
        for (int i = 0; i < values.size(); i++) {
            result.setParameter(i + 1, values.get(i));
        }

        List<Object> values2 = countQuery.getBindValues();
        for (int j = 0; j < values2.size(); j++) {
            countResult.setParameter(j + 1, values2.get(j));
        }

        ResultPage resultPage = new ResultPage();
        resultPage.setContent(result.getResultList());
        resultPage.setSize(size);
        resultPage.setOffset(offset);
        resultPage.setTotalElements((BigInteger) countResult.getSingleResult());

        return resultPage;
    }

    @GetMapping(path = "/all")
    public @ResponseBody List getAllMovies(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "500") Integer size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String imdbId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String poster
    ) {
        List<Condition> conditions = new ArrayList<>();

        if (isNotBlank(title)) {
            conditions.add(field("title").like("%" + title + "%"));
        }
        if (startDate != null) {
            conditions.add((Condition) localDateTimeDiff(startDate.atStartOfDay(), localDateTime(String.valueOf(field("time")))));
        }
        if (isNotBlank(imdbId)) {
            conditions.add(field("imdbId").like("%" + imdbId));
        }
        if (isNotBlank(type)) {
            conditions.add(field("type").like("%" + type));
        }
        if (isNotBlank(poster)) {
            conditions.add(field("poster").like("%" + poster));
        }

        org.jooq.Query query = selectFrom("movie")
                .where(conditions)
                .orderBy(field("poster"))
                .limit(size)
                ;

        javax.persistence.Query result = entityManager.createNativeQuery(query.getSQL());

        List<Object> values = query.getBindValues();
        for (int i = 0; i < values.size(); i++) {
            result.setParameter(i + 1, values.get(i));
        }

        return result.getResultList();
    }
}
