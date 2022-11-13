package koo.MicroServiceApiGateway.filter;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthorizationHeaderFilter extends AbstractGatewayFilterFactory<AuthorizationHeaderFilter.Config> {

    private Environment env;

    public AuthorizationHeaderFilter(Environment env) {
        super(Config.class); // 부모 클래스의 생성자 실행
        this.env = env;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 사용자의 요청에 jwt가 포함되었는지 체크 만약 포함되었다면 포함된 jwt가 유효한 토큰인지 체크 이후 다 통과하면 맨 마지막에 필터를 통과할 수 있다.
            // 사용자의 요청의 헤더에 토큰이 들어갈 수 있음
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                 return onError(exchange, "No authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authorizationHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
            String jwt = authorizationHeader.replace("Bearer", "");

            if (!isJwtValid(jwt)) {
                return onError(exchange, "JWT token is not valid", HttpStatus.UNAUTHORIZED);
            }

            return chain.filter(exchange); // 통과
        });
    }

    private boolean isJwtValid(String jwt) {
        boolean returnValue = true;
        String subject = null;

        try {
            // jwt에는 subject(userId)와 만료기한이 들어있음(jwt io 사이트에서 토큰값을 복호화하면 확인 가능)
            subject = Jwts.parser().setSigningKey(env.getProperty("token.secret")) // 토큰을 복호화하여 subject 추출하기
                    .parseClaimsJws(jwt).getBody()
                    .getSubject();
        } catch (Exception ex) {
            returnValue = false;
        }

        if (subject == null || subject.isEmpty()) {
            returnValue = false;
        }

        return returnValue;
    }

    // 비동기 처리를 하는 Spring WebFlux에서 데이터를 처리하는 기본적인 두가지 단위 중 하나가 Mono이다.(단일 값) Mono에 우리가 전달하고자 하는 데이터를 담아 전달하면 된다.
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse(); // spring mvc를 쓴다면 HttpServletResponse를 쓰지만 spring webflux에서는 ServerHttpResponse를 사용한다.
        response.setStatusCode(httpStatus);

        log.info(err);
        return response.setComplete(); // setComplete 함수를 통해 Mono 타입으로 반환 가능
    }

    public static class Config {
    }

}
