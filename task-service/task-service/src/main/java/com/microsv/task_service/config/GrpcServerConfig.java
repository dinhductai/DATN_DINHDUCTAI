package com.microsv.task_service.config;

import com.microsv.task_service.grpc.proto.TaskInternalGrpcServiceGrpc;
import com.microsv.task_service.grpc.service.GrpcTaskInternalServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@Slf4j
public class GrpcServerConfig {

    @Value("${grpc.server.port:9090}")
    private int grpcPort;

    @Bean
    public Server grpcServer(GrpcTaskInternalServiceImpl grpcService) throws IOException {
        Server server = ServerBuilder
                .forPort(grpcPort)
                .addService(grpcService)
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down gRPC server...");
            server.shutdown();
        }));

        server.start();
        log.info("gRPC server started on port {}", grpcPort);
        return server;
    }
}
