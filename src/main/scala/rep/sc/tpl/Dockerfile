FROM alpine

ARG JAVA_VERSION=8
ARG SBT_VERSION=1.3.8

RUN echo "==> apk update..." && \
    sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && \
    apk update && \
    apk upgrade

RUN echo "==> install java and python..." && \
    apk add --no-cache openjdk$JAVA_VERSION python3

RUN echo "==> install sbt..." && \
    apk add --no-cache bash && \
    apk add --no-cache --virtual=tmp-dep curl && \
    curl -sL "https://piccolo.link/sbt-$SBT_VERSION.tgz" | tar -zvx -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt && \
    apk del tmp-dep

RUN echo "==> install glibc and libatomic..." && \
    wget -q -O /etc/apk/keys/sgerrand.rsa.pub https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub && \
    wget https://github.com/sgerrand/alpine-pkg-glibc/releases/download/2.31-r0/glibc-2.31-r0.apk && \
    apk add glibc-2.31-r0.apk && \
    apk add libatomic

RUN echo "==> install repchain..." && \
    apk add --no-cache git && \
    cd root && \
    git clone https://gitee.com/BTAJL/repchain.git