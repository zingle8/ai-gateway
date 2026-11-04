package com.zimo.gateway.client.web;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 响应包装器：在写回客户端的同时把响应体缓存一份，用于日志采集。
 */
public class CachedBodyResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream cache = new ByteArrayOutputStream(1024);

    private ServletOutputStream outputStream;
    private PrintWriter writer;

    public CachedBodyResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() 已被调用");
        }
        if (outputStream == null) {
            ServletOutputStream target = super.getOutputStream();
            outputStream = new TeeServletOutputStream(target);
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() 已被调用");
        }
        if (writer == null) {
            writer = new PrintWriter(new java.io.OutputStreamWriter(getOutputStream(), getCharacterEncodingSafe()));
        }
        return writer;
    }

    private Charset getCharacterEncodingSafe() {
        String encoding = getCharacterEncoding();
        try {
            return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    public byte[] getCachedBody() {
        return cache.toByteArray();
    }

    /**
     * 把缓冲中的响应内容刷给客户端（必须在过滤器链执行完后调用）。
     *
     * <p>响应体在写入时已由 {@link TeeServletOutputStream} 实时转发给容器，
     * 这里只负责把 PrintWriter 的字符缓冲刷出去。<b>绝不能</b>再把 cache 写一遍，
     * 否则客户端收到的响应体会翻倍。</p>
     */
    public void flushToClient() throws IOException {
        if (writer != null) {
            writer.flush();
        } else if (outputStream != null) {
            outputStream.flush();
        }
    }

    private class TeeServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream target;

        TeeServletOutputStream(ServletOutputStream target) {
            this.target = target;
        }

        @Override
        public boolean isReady() {
            return target.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            target.setWriteListener(writeListener);
        }

        @Override
        public void write(int b) throws IOException {
            cache.write(b);
            target.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            cache.write(b, off, len);
            target.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            target.flush();
        }

        @Override
        public void close() throws IOException {
            target.close();
        }
    }
}
