package com.butingbe.domain.place.exception;

public class PlaceKeywordNotFoundException extends RuntimeException {

  public PlaceKeywordNotFoundException() {
    super("error.place.keyword_not_found");
  }
}
