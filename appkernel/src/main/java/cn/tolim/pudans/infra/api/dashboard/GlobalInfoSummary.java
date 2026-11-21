package cn.tolim.pudans.infra.api.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Basic site information aggregated from the system info service.")
public record GlobalInfoSummary(
        String siteTitle,
        String siteSubtitle,
        String externalUrl,
        String timezone,
        String version,
        long uptimeSeconds) {

    public static GlobalInfoSummary empty() {
        return new GlobalInfoSummary(null, null, null, null, null, 0L);
    }
}
