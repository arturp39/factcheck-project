package com.example.demo.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Article {

    private Long id;

    private String title;

    private String content;

    private String source;

    private LocalDateTime publishedAt;
}