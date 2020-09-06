package com.dappcraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.Session;


@ServerEndpoint("/{type}/{user}")
@ApplicationScoped
public class WSServer {
    Map<String, Session> sceneSessions = new ConcurrentHashMap<>();
    Map<String, Session> controllerSessions = new ConcurrentHashMap<>();

    private static final Logger LOG = Logger.getLogger(WSServer.class);
    private Gson gson = new Gson();
    @OnOpen
    public void onOpen(Session session, @PathParam("type") String type, @PathParam("user") String user) {
        if(type.equals("scene")) {
            sceneSessions.put(user, session);
            LOG.infov("Open scene socket {0}", user);
        } else if (type.equals("controller")) {
            controllerSessions.put(user, session);
            LOG.infov("Open controller socket {0}", user);
        } else {
            LOG.errorv("Open socket error, unknown type {0}, {1}", type, user);
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("type") String type, @PathParam("user") String user) {
        if(type.equals("scene")) {
            sceneSessions.remove(user);
            LOG.infov("Close scene socket {0}", user);
        } else if (type.equals("controller")) {
            controllerSessions.remove(user);
            LOG.infov("Close controller socket {0}", user);
        } else {
            LOG.errorv("Close socket error, unknown type {0}, {1}", type, user);
        }
    }

    @OnError
    public void onError(Session session, @PathParam("type") String type, @PathParam("user") String user, Throwable throwable) {
        LOG.errorv(throwable, "Socket error, unknown type {0}, {1}", type, user);
        if(type.equals("scene")) {
            sceneSessions.remove(user);
        } else if (type.equals("controller")) {
            controllerSessions.remove(user);
        }
    }

    @OnMessage
    public void onMessage(String message, @PathParam("type") String type, @PathParam("user") String user) {
        if(type.equals("scene")) {
            LOG.infov("onMessage scene socket {0}: {1}", user, message);
        } else if (type.equals("controller")) {
            WsMessage msg = parse(message);
            LOG.infov("controller msg {0}, {1}", user, msg.getType());
            if (sceneSessions.containsKey((user))) {
                if (msg.getType().equals("rotate")) {
                    LOG.infov("send {0}, {1}, {2}, {3}", msg.getQuat()[0], msg.getQuat()[1], msg.getQuat()[2], msg.getQuat()[3]);
                    message = gson.toJson(msg.getQuat());
                    sceneSessions.get(user).getAsyncRemote().sendObject(message, result -> {
                        if (result.getException() != null) {
                            LOG.errorv(result.getException(), "Unable to send message to scene for {0}", user);
                        }
                    });
                }
            }
        } else {
            LOG.errorv("onMessage error, unknown type {0}, {1}", type, user);
        }
    }

    private WsMessage parse(String json) {

        return gson.fromJson(json, WsMessage.class);
    }

    private void broadcast(String message) {
        sceneSessions.values().forEach(s -> {
            s.getAsyncRemote().sendObject(message, result -> {
                if (result.getException() != null) {
                    System.out.println("Unable to send message: " + result.getException());
                }
            });
        });
    }
}
