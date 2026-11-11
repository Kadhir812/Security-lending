package com.hexaware.borrower_service.dto.response;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserView user) {

  public record UserView(
      String username, String email, String organizationName, String organizationReference) {}
}
