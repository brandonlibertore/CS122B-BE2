package com.github.klefstad_teaching.cs122b.movies.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.klefstad_teaching.cs122b.movies.model.data.MovieObjects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MovieRepo
{

    private ObjectMapper objectMapper;
    private NamedParameterJdbcTemplate template;

    @Autowired
    public MovieRepo(ObjectMapper objectMapper, NamedParameterJdbcTemplate template)
    {
        this.objectMapper = objectMapper;
        this.template = template;
    }

    public List<MovieObjects> searchMovie(String sql, MapSqlParameterSource source){
        List<MovieObjects> movies = this.template.query(sql,
                source, (rs, rowNum) ->
                new MovieObjects()
                        .setId(rs.getLong("id"))
                        .setTitle(rs.getString("title"))
                        .setYear(rs.getInt("year"))
                        .setDirector(rs.getString("name"))
                        .setRating(rs.getDouble("rating"))
                        .setBackdropPath(rs.getString("backdrop_path"))
                        .setPosterPath(rs.getString("poster_path"))
                        .setHidden(rs.getBoolean("hidden")));
        return movies;
    }
}
