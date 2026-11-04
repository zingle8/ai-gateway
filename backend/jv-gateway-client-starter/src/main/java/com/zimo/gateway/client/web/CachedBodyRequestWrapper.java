package com.zimo.gateway.client.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 请求包装器：把请求体缓存起来，使其可以被重复读取。
 */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream() == null ? new byte[0] : request.getInputStream().readAllBytes();
    }

    public byte[] getCachedBody() {
        return body;
    }

    public String getCachedBodyAsString(Charset charset) {
        return new String(body, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream source = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read() {
                return source.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = StandardCharsets.UTF_8;
        if (encoding != null && !encoding.isEmpty()) {
            try {
                charset = Charset.forName(encoding);
            } catch (Exception ignored) {
                // 回落 UTF-8
            }
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}
