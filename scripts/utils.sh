#!/usr/bin/env bash

ensure_tool(){
    cmd="$1"
    package="$2"
    if command -v "${cmd}" >/dev/null; then
        return
    fi
    if command -v apt-get; then
        sudo apt-get update; sudo apt-get -y install ${package}
    else
        echo "Unable to install ${cmd}, apt-get not detected if you're on a mac, use 'brew install ${package}'"
        return 1
    fi
}

ensure_mvn(){
    # skip if already configured
    command -v mvn && grep -q conjur_jenkins ~/.m2/settings.xml 2>/dev/null && return

    # Retrieve the latest mvn release version
    mvn_latest=$(curl -s \
        https://repo1.maven.org/maven2/org/apache/maven/maven/maven-metadata.xml | \
        grep '<version>[0-9]\+\.[0-9]\+\.[0-9]\+</version>' | \
        tail -1 | \
        cut -d ">" -f2 | \
        cut -d "<" -f1 | \
        tr -d '\r\n')
    echo "Latest detected maven version: ${mvn_latest}"
    # Install mvn cli
    mvn_version="${1:-${mvn_latest}}"
    echo "Installing maven version: ${mvn_version}"
    curl "https://dlcdn.apache.org/maven/maven-3/${mvn_version}/binaries/apache-maven-${mvn_version}-bin.tar.gz" > maven.tgz
    tar xzf maven.tgz
    export PATH="${PATH}:${PWD}/apache-maven-${mvn_version}/bin"

    # Get mvn creds from conjurops
    mkdir -p ~/.m2/
    if [[ -f ~/.m2/settings.xml ]]; then
        echo "Warning ~/.m2/settings.xml already exists and will be overwritten"
    fi
    /usr/local/lib/summon/summon-conjur ci/upstream-jenkins/maven-config |base64 -d > ~/.m2/settings.xml

}

ensure_xmlstarlet(){
    ensure_tool xmlstarlet xmlstarlet
}
