package kth.nova.overloadalert.data.auth

data class TokenData(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpirationTime: Long
)