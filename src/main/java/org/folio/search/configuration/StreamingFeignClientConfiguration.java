package org.folio.search.configuration;

import static java.nio.charset.StandardCharsets.UTF_8;

import feign.Feign;
import feign.codec.Decoder;
import feign.optionals.OptionalDecoder;
import feign.stream.StreamDecoder;
import java.io.BufferedReader;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;

public class StreamingFeignClientConfiguration {

  @Bean
  public Decoder feignDecoder(ObjectProvider<HttpMessageConverterCustomizer> customizers,
                              ObjectFactory<HttpMessageConverters> messageConverters) {
    return StreamDecoder.create((r, t) -> {
        BufferedReader bufferedReader = new BufferedReader(r.body().asReader(UTF_8));
        return bufferedReader.lines().iterator();
      },
      new OptionalDecoder(new ResponseEntityDecoder(new SpringDecoder(messageConverters, customizers)))
    );
  }

  @Bean
  public Feign.Builder builder() {
    return Feign.builder()
      .doNotCloseAfterDecode();
  }
}
