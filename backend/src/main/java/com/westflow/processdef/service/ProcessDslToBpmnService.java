package com.westflow.processdef.service;

import com.westflow.processdef.model.ProcessDslPayload;
import java.util.Comparator;
import org.springframework.stereotype.Service;

@Service
public class ProcessDslToBpmnService {

    public String convert(ProcessDslPayload payload, String processDefinitionId, int version) {
        StringBuilder builder = new StringBuilder();
        builder.append("<process id=\"")
                .append(escape(processDefinitionId))
                .append("\" key=\"")
                .append(escape(payload.processKey()))
                .append("\" name=\"")
                .append(escape(payload.processName()))
                .append("\" version=\"")
                .append(version)
                .append("\">");

        payload.nodes().stream()
                .sorted(Comparator.comparing(ProcessDslPayload.Node::id))
                .forEach(node -> builder.append("<node id=\"")
                        .append(escape(node.id()))
                        .append("\" type=\"")
                        .append(escape(node.type()))
                        .append("\" name=\"")
                        .append(escape(node.name()))
                        .append("\"/>"));

        payload.edges().stream()
                .sorted(Comparator.comparing(ProcessDslPayload.Edge::id))
                .forEach(edge -> builder.append("<transition id=\"")
                        .append(escape(edge.id()))
                        .append("\" source=\"")
                        .append(escape(edge.source()))
                        .append("\" target=\"")
                        .append(escape(edge.target()))
                        .append("\"/>"));

        builder.append("</process>");
        return builder.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
