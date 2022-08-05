package com.github.klefstad_teaching.cs122b.movies.rest;

import com.github.klefstad_teaching.cs122b.core.error.ResultError;
import com.github.klefstad_teaching.cs122b.core.result.MoviesResults;
import com.github.klefstad_teaching.cs122b.core.security.JWTManager;
import com.github.klefstad_teaching.cs122b.movies.model.data.MovieObjects;
import com.github.klefstad_teaching.cs122b.movies.model.data.SearchOrderBy;
import com.github.klefstad_teaching.cs122b.movies.model.response.MovieSearchResponse;
import com.github.klefstad_teaching.cs122b.movies.repo.MovieRepo;
import com.github.klefstad_teaching.cs122b.movies.util.Validate;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

@RestController
public class MovieController
{
    private final MovieRepo repo;
    private final Validate validate;

    @Autowired
    public MovieController(MovieRepo repo, Validate validate)
    {
        this.repo = repo;
        this.validate = validate;
    }

    @GetMapping("/movie/search")
    public ResponseEntity<MovieSearchResponse> movieSearch(@AuthenticationPrincipal SignedJWT user,
                                                           @RequestParam Optional<String> title, @RequestParam Optional<Integer> year,
                                                           @RequestParam Optional<String> director, @RequestParam Optional<String> genre,
                                                           @RequestParam Optional<Integer> limit, @RequestParam Optional<Integer> page,
                                                           @RequestParam Optional<String> orderBy, @RequestParam Optional<String> direction)
    {

        // Validate the data entries:
        validate.validityCheck(limit, page, orderBy, direction);

        // Initialize arguments to be passed into template.query:
        StringBuilder         sql;
        MapSqlParameterSource source     = new MapSqlParameterSource();
        boolean               whereAdded = false;

        // Begin creating sql statement:

        //User is querying with director and genre selected.
        final String MOVIE_WITH_GENRE =
                "SELECT m.id, m.title, m.year, p.name, m.rating, m.backdrop_path, m.poster_path, m.hidden " +
                "FROM movies.movie m " +
                "   JOIN movies.person p ON m.director_id = p.id " +
                "   JOIN movies.movie_genre mg ON m.id = mg.movie_id " +
                "   JOIN movies.genre g ON mg.genre_id = g.id ";

        final String MOVIE_NO_GENRE =
                "SELECT m.id, m.title, m.year, p.name, m.rating, m.backdrop_path, m.poster_path, m.hidden " +
                "FROM movies.movie m " +
                "   JOIN movies.person p ON m.director_id = p.id ";

        // Query to get list of movies that fit the parameters:

        // First Check if genre is present so that we can join our tables
        // movies.movie, movies.movie_genre, movies.genre, movies.person:

        // If Genre is present:
        if (genre.isPresent()){
            sql = new StringBuilder(MOVIE_WITH_GENRE);
            sql.append(" WHERE g.name LIKE :genre ");
            String wildcardSearch = '%' + genre.get() + '%';
            source.addValue("genre", wildcardSearch, Types.VARCHAR);
            whereAdded = true;
        }
        // If Genre is not present:
        else{
            sql = new StringBuilder(MOVIE_NO_GENRE);
        }

        // BEGIN TO ADD WHERE STATEMENTS:

        // Check if user is searching for title:
        if (title.isPresent()){
            if (whereAdded){
                sql.append(" AND ");
            }
            else{
                sql.append(" WHERE ");
                whereAdded = true;
            }
            sql.append(" m.title LIKE :title ");
            String wildcardSearch = '%' + title.get() + '%';
            source.addValue("title", wildcardSearch, Types.VARCHAR);
        }

        // Check if user is searching for year:
        if (year.isPresent()){
            if (whereAdded){
                sql.append(" AND ");
            }
            else{
                sql.append(" WHERE ");
                whereAdded = true;
            }
            sql.append(" m.year = :year ");
            source.addValue("year", year.get(), Types.INTEGER);
        }

        // Check if user is searching for director:
        if (director.isPresent()){
            if (whereAdded){
                sql.append(" AND ");
            }
            else{
                sql.append(" WHERE ");
                whereAdded = true;
            }
            sql.append(" p.name LIKE :director ");
            String wildcardSearch = '%' + director.get() + '%';
            source.addValue("director", wildcardSearch, Types.VARCHAR);
        }

        // Check if user is searching for genre:
        if (genre.isPresent()){
            if (whereAdded){
                sql.append(" AND ");
            }
            else{
                sql.append(" WHERE ");
                whereAdded = true;
            }
            sql.append(" g.name LIKE :genre ");
            String wildcardSearch = '%' + genre.get() + '%';
            source.addValue("genre", wildcardSearch, Types.VARCHAR);
        }

        // Check if user is either admin/employee or regular user account:
        try{
            List<String> roles = user.getJWTClaimsSet().getStringListClaim(JWTManager.CLAIM_ROLES);
            if (whereAdded){
                sql.append(" AND ");
            }
            else{
                sql.append(" WHERE ");
                whereAdded = true;
            }
            if (roles.size() > 0){
                if (roles.contains("ADMIN") | roles.contains("EMPLOYEE")){
                    sql.append(" (m.hidden = false OR m.hidden = true) ");
                }
                else{
                    sql.append(" m.hidden = false ");
                }
            }
            else{
                sql.append(" m.hidden = false ");
            }
        }
        catch (Exception e){
            sql.append(" m.hidden = false");
        }

        // Create our order by sql statement:
        SearchOrderBy orderByString = SearchOrderBy.fromString(orderBy, direction);
        sql.append(orderByString.toSql());

        // If limit is given:
        if (limit.isPresent()){
            sql.append(" LIMIT :limit ");
            source.addValue("limit", limit.get(), Types.INTEGER);
        }

        // If limit and page are given:
        if (limit.isPresent() && page.isPresent()){
            sql.append(" OFFSET :page ");
            source.addValue("page", ((page.get() - 1) * limit.get()), Types.INTEGER);
        }

        // If limit is not given, but page is give:
        if (!limit.isPresent() && page.isPresent()){
            sql.append(" LIMIT 10 OFFSET :page");
            source.addValue("page", ((page.get() - 1) * 10), Types.INTEGER);
        }

        // If limit and page are both not given:
        if (!limit.isPresent() && !page.isPresent()){
            sql.append(" LIMIT 10 OFFSET 0");
        }

        //Create the list of movies objects:
        List<MovieObjects> movies = repo.searchMovie(sql.toString(), source);

        // If our movie list is empty throw that no movies were found:
        if (movies.size() < 1){
            throw new ResultError(MoviesResults.NO_MOVIES_FOUND_WITHIN_SEARCH);
        }

        // Create response for movies found:
        MovieSearchResponse body = new MovieSearchResponse()
                .setResult(MoviesResults.MOVIES_FOUND_WITHIN_SEARCH)
                .setMovies(movies);

        // Return our response:
        return ResponseEntity
                .status(body.getResult().status())
                .body(body);
    }
}
