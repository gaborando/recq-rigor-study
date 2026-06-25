package com.study.app.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * The public REST contract. The edge owns no data; it forwards each call to the
 * service that owns the resource (orders / inventory / customers) and relays the
 * peer's status and body verbatim, so backend 201/202/400/404 propagate unchanged.
 */
@RestController
public class ProxyController {

    private final RestClient orders;
    private final RestClient inventory;
    private final RestClient customers;

    public ProxyController(RestClient ordersRestClient, RestClient inventoryRestClient,
                           RestClient customersRestClient) {
        this.orders = ordersRestClient;
        this.inventory = inventoryRestClient;
        this.customers = customersRestClient;
    }

    @RequestMapping({"/products", "/products/**", "/customers", "/customers/**",
                     "/orders", "/orders/**", "/stats", "/stats/**"})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) {
        RestClient target = route(request.getRequestURI());
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String uri = query == null ? path : path + "?" + query;
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        RestClient.RequestBodySpec spec = target.method(method).uri(uri);

        String contentType = request.getContentType();
        if (contentType != null) {
            spec.contentType(MediaType.parseMediaType(contentType));
        }
        if (body != null && body.length > 0) {
            spec.body(body);
        }

        // exchange() bypasses the default error handlers, so 4xx/5xx are relayed
        // as-is rather than thrown.
        return spec.exchange((req, res) -> {
            byte[] responseBody = res.getBody().readAllBytes();
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(res.getStatusCode());
            MediaType ct = res.getHeaders().getContentType();
            if (ct != null) {
                builder.contentType(ct);
            }
            return builder.body(responseBody);
        });
    }

    private RestClient route(String path) {
        if (path.startsWith("/products")) return inventory;
        if (path.startsWith("/customers")) return customers;
        return orders;   // /orders and /stats
    }
}
