package com.pgdr.reconstruccion.adaptadores.salida.mensajeria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Intercambio de avisos del módulo y cola de la unidad de integración, que los lleva a los
 * sistemas financiero y de contratación. La cola existe desde ya para que ningún aviso se
 * pierda mientras esa unidad no esté construida.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "reconstruccion.mensajeria.habilitada", havingValue = "true")
public class ConfiguracionMensajeria {

    @Bean
    TopicExchange intercambioReconstruccion() {
        return new TopicExchange(DespachadorBandeja.INTERCAMBIO, true, false);
    }

    @Bean
    Queue colaIntegracionReconstruccion() {
        return new Queue("integracion.reconstruccion", true);
    }

    @Bean
    Binding enlaceIntegracionReconstruccion(Queue colaIntegracionReconstruccion,
                                            TopicExchange intercambioReconstruccion) {
        return BindingBuilder.bind(colaIntegracionReconstruccion).to(intercambioReconstruccion).with("#");
    }
}
