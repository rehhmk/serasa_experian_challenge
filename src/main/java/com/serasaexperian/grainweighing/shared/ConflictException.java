package com.serasaexperian.grainweighing.shared;

/**
 * Estado do recurso conflita com a operação pedida — distinto de
 * BusinessRuleViolationException (422): aqui a operação em si é válida, só
 * não pode ser aplicada agora porque outro recurso já ocupa o lugar dela
 * (ex: já existe uma TransportTransaction OPEN para o truck). Mapeada para
 * 409 (LOG-020).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
