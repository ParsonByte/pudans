package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.IdpQrPollRequest;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrPollResponse;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrStartRequest;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrStartResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.idp.IdpQrLoginService;
import cn.tolim.pudans.iam.infra.context.ServletContextService;
import cn.tolim.pudans.iam.security.IamRequestUtils;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;

@Tag(name = "IAM 登录认证")
@RestController
public class IamIdpQrLoginEndpoint {

    private final IdpQrLoginService idpQrLoginService;
    private final IamSessionCookieService cookieService;

    public IamIdpQrLoginEndpoint(IdpQrLoginService idpQrLoginService,
                                 IamSessionCookieService cookieService) {
        this.idpQrLoginService = idpQrLoginService;
        this.cookieService = cookieService;
    }

    // Creates a QR login state and provider authorization URL
    // Parameter request: IdpQrStartRequest
    // Parameter httpRequest: HttpServletRequest
    // Returns: IdpQrStartResponse
    @Operation(summary = "开始扫码登录", operationId = "startIdpQrLogin")
    @PostMapping(path = IamAuthApiPaths.IDP_QR_START)
    public IdpQrStartResponse start(@RequestBody IdpQrStartRequest request,
                                    HttpServletRequest httpRequest) {
        return idpQrLoginService.start(request, ServletContextService.getServerUrl(httpRequest));
    }

    // Detects the scanner app or shows a provider chooser for a universal QR URL
    // Parameter state: String
    // Parameter provider: String
    // Parameter httpRequest: HttpServletRequest
    // Returns: ResponseEntity
    @Operation(summary = "识别扫码应用", operationId = "scanIdpQrLogin")
    @GetMapping(path = IamAuthApiPaths.IDP_QR_SCAN)
    public ResponseEntity<String> scan(@RequestParam("state") String state,
                                       @RequestParam(value = "provider", required = false)
                                       String provider,
                                       HttpServletRequest httpRequest) {
        IdpQrLoginService.IdpScanResult result = idpQrLoginService.scan(state, provider,
                IamRequestUtils.userAgent(httpRequest));
        if (result.redirectUrl() != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(result.redirectUrl()))
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(result.html());
    }

    // Completes the provider redirect and binds the platform account to the state
    // Parameter code: String
    // Parameter state: String
    // Returns: ResponseEntity
    @Operation(summary = "扫码登录回调", operationId = "completeIdpQrLogin")
    @GetMapping(path = IamAuthApiPaths.IDP_QR_CALLBACK, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "authCode", required = false) String authCode,
            @RequestParam("state") String state) {
        String authorizationCode = StringUtils.isNotBlank(code) ? code : authCode;
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(idpQrLoginService.callback(authorizationCode, state));
    }

    // Polls the QR login state and creates the desktop session when complete
    // Parameter request: IdpQrPollRequest
    // Parameter httpRequest: HttpServletRequest
    // Parameter httpResponse: HttpServletResponse
    // Returns: IdpQrPollResponse
    @Operation(summary = "轮询扫码登录状态", operationId = "pollIdpQrLogin")
    @PostMapping(path = IamAuthApiPaths.IDP_QR_POLL)
    public IdpQrPollResponse poll(@RequestBody IdpQrPollRequest request,
                                  HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        IdpQrPollResponse result = idpQrLoginService.poll(request,
                IamRequestUtils.clientIp(httpRequest), IamRequestUtils.userAgent(httpRequest));
        LoginResponse login = result.getLogin();
        if (login != null && login.getSid() != null && !login.getSid().isBlank()) {
            cookieService.writeSessionCookie(httpResponse, login.getSid(), login.getExpiresAt());
        }
        return result;
    }
}