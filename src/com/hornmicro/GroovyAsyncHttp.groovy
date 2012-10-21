package com.hornmicro

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CharsetEncoder

import org.codehaus.groovy.runtime.StackTraceUtils

class GroovyAsyncHttp {
    ByteBuffer buf = ByteBuffer.allocate(2048)
    Charset charset = Charset.forName("UTF-8")
    CharsetDecoder decoder = charset.newDecoder()
    CharsetEncoder encoder = charset.newEncoder()
    Selector selector = Selector.open()
    ServerSocketChannel server = ServerSocketChannel.open()
    Closure handler
    
    private GroovyAsyncHttp() {
        
    }
    
    static GroovyAsyncHttp createHttpServer(Closure handler = null) {
        GroovyAsyncHttp instance = new GroovyAsyncHttp()
        instance.handler = handler
        return instance
    }
    
    void listen(int port) {
        listen(new InetSocketAddress(port))
    }
    
    void listen(String hostname, int port) {
        listen(new InetSocketAddress(hostname, port))
    }
    
    void listen(InetSocketAddress address) {
        server.socket().bind(address)
        server.configureBlocking(false)
        server.register(selector, SelectionKey.OP_ACCEPT)
        pump()
    }
    
    void pump() {
        try {
            while(true) {
                if(!selector.select()) {
                    continue 
                }
                Iterator<SelectionKey> i = selector.selectedKeys().iterator()
                while (i.hasNext()) {
                    SelectionKey key = i.next()
                    i.remove() // remove key as it has been handled.
                    if (!key.isValid()) {
                        continue
                    }
                    try {
                        
                        // ACCEPT
                        if (key.isAcceptable()) {
                            SocketChannel client = ((ServerSocketChannel) key.channel()).accept()
                            if(client != null) {
                                client.configureBlocking(false)
                                client.register(selector, SelectionKey.OP_READ)
                            }
                        }
                        
                        // READ
                        if (key.isReadable()) {
                            SocketChannel client = (SocketChannel) key.channel()
                            buf.clear()
                            int numread
                            while ((numread = client.read (buf)) > 0) {
                                // Read until there is no more
                            }
                            buf.flip()
                            String request = decoder.decode(buf).toString()
                            if(handler) {
                                handler([
                                    client:client, 
                                    request: request,
                                    writeString: { s ->
                                        client.write(encoder.encode(CharBuffer.wrap(s)))
                                    }
                                ]) 
                            } else {
                                int length = request.bytes.size()
                                client.write(encoder.encode(CharBuffer.wrap(
                                    "HTTP/1.1 200 OK\r\n"+
                                    /*"Content-Length: ${length}"+*/
                                    "\r\n${request}"
                                )))
                            }
                        }
                          
                    } catch(e) {
                        System.err.println("Error handling client: " + key.channel())
                        StackTraceUtils.deepSanitize(e)
                        e.printStackTrace()
                    } finally {
                        if (key.channel() instanceof SocketChannel) {
                            key.channel().close()
                        }
                    }
                }
            }
                
        } catch (IOException ex) {
            StackTraceUtils.deepSanitize(ex)
            ex.printStackTrace()
        } finally {
            shutdown()
        }
    }
    
    void shutdown() {
        try {
            selector.close()
            server.close()
        } catch (IOException ex) {
            // do nothing, its game over
        }
    }
    static main(args) {
        GroovyAsyncHttp.createHttpServer { req ->
            req.writeString(
                "HTTP/1.1 200 OK\r\n\r\n"+
                "Hello World"
            )
        }.listen(5555)
    }
}
