package com.opencloud.api.gateway.exception;

import com.opencloud.api.gateway.service.AccessLogService;
import com.opencloud.common.constants.ResultEnum;
import com.opencloud.common.exception.OpenGlobalExceptionHandler;
import com.opencloud.common.model.ResultBody;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * @author liuyadu
 * @classDesc: 统一异常处理
 */
@Slf4j
public class JsonExceptionHandler implements ErrorWebExceptionHandler {

    private AccessLogService accessLogService;

    public JsonExceptionHandler(AccessLogService accessLogService) {
        this.accessLogService = accessLogService;
    }

    /**
     * MessageReader
     */
    private List<HttpMessageReader<?>> messageReaders = Collections.emptyList();

    /**
     * MessageWriter
     */
    private List<HttpMessageWriter<?>> messageWriters = Collections.emptyList();

    /**
     * ViewResolvers
     */
    private List<ViewResolver> viewResolvers = Collections.emptyList();

    /**
     * 存储处理异常后的信息
     */
    private ThreadLocal<ResultBody> exceptionHandlerResult = new ThreadLocal<>();

    /**
     * 参考AbstractErrorWebExceptionHandler
     *
     * @param messageReaders
     */
    public void setMessageReaders(List<HttpMessageReader<?>> messageReaders) {
        Assert.notNull(messageReaders, "'messageReaders' must not be null");
        this.messageReaders = messageReaders;
    }

    /**
     * 参考AbstractErrorWebExceptionHandler
     *
     * @param viewResolvers
     */
    public void setViewResolvers(List<ViewResolver> viewResolvers) {
        this.viewResolvers = viewResolvers;
    }

    /**
     * 参考AbstractErrorWebExceptionHandler
     *
     * @param messageWriters
     */
    public void setMessageWriters(List<HttpMessageWriter<?>> messageWriters) {
        Assert.notNull(messageWriters, "'messageWriters' must not be null");
        this.messageWriters = messageWriters;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        /**
         * 按照异常类型进行处理
         */
        ResultBody resultBody;
        ServerHttpRequest request = exchange.getRequest();
        if (ex instanceof NotFoundException) {
            resultBody = ResultBody.failed().code(ResultEnum.SERVICE_UNAVAILABLE.getCode()).msg(ResultEnum.SERVICE_UNAVAILABLE.getMessage()).httpStatus(HttpStatus.SERVICE_UNAVAILABLE.value()).path(request.getURI().getPath());
            log.error("==> 错误解析:{}", resultBody);
        } else {
            resultBody = OpenGlobalExceptionHandler.resolveException((Exception) ex, exchange.getRequest().getURI().getPath());
        }
        /**
         * 参考AbstractErrorWebExceptionHandler
         */
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        exceptionHandlerResult.set(resultBody);
        ServerRequest newRequest = ServerRequest.create(exchange, this.messageReaders);
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse).route(newRequest)
                .switchIfEmpty(Mono.error(ex))
                .flatMap((handler) -> handler.handle(newRequest))
                .flatMap((response) -> {
                    return write(exchange, response,ex);
                });
    }

    /**
     * 参考DefaultErrorWebExceptionHandler
     *
     * @param request
     * @return
     */
    protected Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        ResultBody result = exceptionHandlerResult.get();
        return ServerResponse.status(result.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .body(BodyInserters.fromObject(result));
    }

    /**
     * 参考AbstractErrorWebExceptionHandler
     *
     * @param exchange
     * @param response
     * @return
     */
    private Mono<? extends Void> write(ServerWebExchange exchange,
                                       ServerResponse response,Throwable ex) {
        exchange.getResponse().getHeaders()
                .setContentType(response.headers().getContentType());
        // 保存日志
        accessLogService.sendLog(exchange, (Exception) ex);
        return response.writeTo(exchange, new ResponseContext());
    }

    /**
     * 参考AbstractErrorWebExceptionHandler
     */
    private class ResponseContext implements ServerResponse.Context {

        @Override
        public List<HttpMessageWriter<?>> messageWriters() {
            return JsonExceptionHandler.this.messageWriters;
        }

        @Override
        public List<ViewResolver> viewResolvers() {
            return JsonExceptionHandler.this.viewResolvers;
        }

    }
}
