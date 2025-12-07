package com.ianctchinese.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TencentMapClient {

  @Value("${tmap.key}")
  private String apiKey;

  @Value("${tmap.sk}")
  private String secretKey;

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  private static final String GEOCODER_PATH = "/ws/geocoder/v1/";
  private static final String BASE_URL = "https://apis.map.qq.com";

  public TencentMapClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  public GeocodingResult geocode(String address) {
    if (address == null || address.isBlank()) {
      return null;
    }

    try {
      String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8);
      String url;

      // 如果配置了 SK，使用签名验证；否则直接使用 Key
      if (secretKey != null && !secretKey.isBlank()) {
        String rawQueryParams = "address=" + address + "&key=" + apiKey;
        String sig = calculateSignature(GEOCODER_PATH, rawQueryParams);
        url = BASE_URL + GEOCODER_PATH + "?address=" + encodedAddress + "&key=" + apiKey + "&sig=" + sig;
      } else {
        url = BASE_URL + GEOCODER_PATH + "?address=" + encodedAddress + "&key=" + apiKey;
      }

      log.debug("Geocoding request: address={}, url={}", address, url);

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .GET()
          .timeout(Duration.ofSeconds(10))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body();

      log.debug("Geocoding response: {}", body);

      JsonNode root = objectMapper.readTree(body);
      int status = root.path("status").asInt(-1);

      if (status != 0) {
        String message = root.path("message").asText("Unknown error");
        log.warn("Geocoding failed for '{}': status={}, message={}", address, status, message);
        return null;
      }

      JsonNode location = root.path("result").path("location");
      double lat = location.path("lat").asDouble(Double.NaN);
      double lng = location.path("lng").asDouble(Double.NaN);

      if (Double.isNaN(lat) || Double.isNaN(lng)) {
        log.warn("Geocoding returned invalid coordinates for '{}'", address);
        return null;
      }

      String resolvedAddress = root.path("result").path("address").asText("");
      log.info("Geocoding success: '{}' -> ({}, {}), resolved: {}", address, lat, lng, resolvedAddress);

      return new GeocodingResult(lat, lng, resolvedAddress);

    } catch (Exception e) {
      log.error("Geocoding error for '{}': {}", address, e.getMessage());
      return null;
    }
  }

  /**
   * 计算腾讯地图 API 签名
   * 签名算法：MD5(请求路径 + "?" + 请求参数 + SK)
   */
  private String calculateSignature(String path, String queryParams) {
    try {
      String signatureSource = path + "?" + queryParams + secretKey;
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(signatureSource.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      log.error("Failed to calculate signature: {}", e.getMessage());
      return "";
    }
  }

  public record GeocodingResult(double latitude, double longitude, String resolvedAddress) {}
}

