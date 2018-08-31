package rep.ui.web;


import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.Connector;
import rep.api.rest.IotService;
import rep.api.rest.IotService.*;
import rep.api.rest.RestIot;
import rep.app.conf.SystemProfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zyf
 */
public class IotServerJava extends AbstractActor {

    public static void start(ActorSystem sys, String hostname, int port) {
        try {
            ActorRef Iot = sys.actorOf(Props.create(RestIot.class), "coap");  // 可能有问题，示范性的，笔记
            Process process = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr \""+ 5683 +"\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            if (reader.readLine() == null) {
                final CoapServer server = new CoapServer();
                CoapEndpoint.CoapEndpointBuilder builder = new CoapEndpoint.CoapEndpointBuilder();
                builder.setInetSocketAddress(new InetSocketAddress("localhost", 5683));
                server.addEndpoint(builder.build());
                server.add(new IotService(Iot).getCoapResource());
                server.start();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Coap Server online at coap://" + hostname + ":" + port);
    }

    @Override
    public void preStart() {
        IotServer.start(getContext().getSystem(),SystemProfile.getCoapServiceHost(), SystemProfile.getCoapServicePort());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(String.class, msg->{
            //收到消息
            System.out.println(self()+"  receive msg  from "+sender()+": "+ msg);
        }).matchAny(msg ->{

        }).build();
    }
}
