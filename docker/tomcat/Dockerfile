FROM tomcat:9-jdk17

#Add imagemagick for generating thumbnails
RUN apt-get update && apt-get install -y \
  imagemagick \
  && rm -rf /var/lib/apt/lists/*

#TODO: figure out how to use tomcat instead of zfishweb (needs postgresql permission)
RUN addgroup --gid 1000 zfin && \
    adduser --home /usr/local/tomcat --no-create-home --ingroup zfin --disabled-password --gecos '' zfishweb

USER zfishweb

COPY ./postgresql-42.2.18.jar  /usr/local/tomcat/lib
