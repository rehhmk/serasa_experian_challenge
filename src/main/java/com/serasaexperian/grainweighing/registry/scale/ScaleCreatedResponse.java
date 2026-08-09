package com.serasaexperian.grainweighing.registry.scale;

import java.util.UUID;

/**
 * apiKey em texto puro só é retornado aqui, na criação — a partir desse momento
 * só o hash é persistido/consultável. O operador precisa copiar e configurar o
 * ESP32 imediatamente (LOG-014).
 */
public record ScaleCreatedResponse(String id, UUID branchId, String apiKey) {
}
