package br.com.projetoia.api;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.serialization.SerializationException;
import com.agui.community.core.serialization.Serializer;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * Endpoint AG-UI: recebe um RunAgentInput em JSON e responde com um stream SSE
 * em que cada evento e um JSON do protocolo.
 */
@Path("/api/agent")
public class AgentResource {

    @Inject
    Agent agent;

    @Inject
    Serializer serializer;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> run(String body) {
        RunAgentInput input;
        try {
            input = serializer.deserialize(body, RunAgentInput.class);
        } catch (SerializationException e) {
            throw new BadRequestException("Corpo da requisição não é um RunAgentInput válido.", e);
        }
        return Multi.createFrom().publisher(agent.run(input)).map(serializer::serialize);
    }
}
