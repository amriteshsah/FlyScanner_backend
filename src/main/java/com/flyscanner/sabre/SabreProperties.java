package com.flyscanner.sabre;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flyscanner.sabre")
public class SabreProperties {
    private boolean enabled = false;
    private String baseUrl = "https://api.cert.platform.sabre.com";
    private String tokenUrl = "https://api.cert.platform.sabre.com/v2/auth/token";
    private String username = "";
    private String group = "";
    private String domain = "";
    private String password = "";
    private int maxOffers = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getMaxOffers() { return maxOffers; }
    public void setMaxOffers(int maxOffers) { this.maxOffers = maxOffers; }
}
