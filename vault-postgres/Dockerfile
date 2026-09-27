# vault-postgres — PostgreSQL 17 with PostGIS and pgvector from PGDG, preloading no library (C-IMAGES, C-POSTGRES).
#
# Pin the Debian base, not the floating `postgres:17` tag. The floating tag moves across Debian
# releases, which bumps glibc — and glibc provides the default collation. A glibc change makes the
# image binary-incompatible with an existing data volume (collation-version mismatch → createdb fails).
# `-trixie` freezes glibc at the release the floating tag currently resolves to, so rebuilds stay
# compatible with the same volume. Bump this tag deliberately (and re-init the volume) — never by drift.
#
# The base is named by its multi-arch index digest, and every package the layer adds — the two asked for and every
# dependency apt pulls in with them — is named `name=version`, from Debian at a fixed snapshot.debian.org timestamp
# and PGDG from apt-archive.postgresql.org, which keeps every published version. A bump is a commit (BFR-004).
FROM postgres:17-trixie@sha256:d74eeac9a635390a49bc21bd49fccd973de707e2a53a76ac49b552b8712ec46f

ARG TARGETARCH

LABEL org.opencontainers.image.source=https://github.com/libragraph-com/images

# The frozen sources live in a scratch apt configuration the layer removes, so the base's own apt sources are left as
# they are. apt-archive.postgresql.org serves only HTTPS and the base carries no CA bundle, so apt trusts that one
# host's root, Amazon Root CA 1, mounted from the build context for this step only; every package is still verified
# against the Debian and PGDG signing keys the base carries.
#
# libarmadillo14 is the one package whose version differs by architecture (a binNMU built for amd64 alone), so it is
# pinned per platform; its dependencies are installed first so that step adds it alone.
RUN --mount=type=bind,source=apt-archive-ca.pem,target=/run/apt-archive-ca.pem \
    set -eux; \
    mkdir -p /tmp/pin/sources.list.d; \
    echo 'deb [signed-by=/usr/share/keyrings/debian-archive-keyring.pgp] http://snapshot.debian.org/archive/debian/20260918T000000Z trixie trixie-updates main' > /tmp/pin/sources.list; \
    echo 'deb [signed-by=/usr/share/keyrings/debian-archive-keyring.pgp] http://snapshot.debian.org/archive/debian-security/20260918T000000Z trixie-security main' >> /tmp/pin/sources.list; \
    echo 'deb [signed-by=/usr/local/share/keyrings/postgres.gpg.asc] https://apt-archive.postgresql.org/pub/repos/apt trixie-pgdg-archive main' >> /tmp/pin/sources.list; \
    printf '%s\n' \
        'Dir::Etc::SourceList "/tmp/pin/sources.list";' \
        'Dir::Etc::SourceParts "/tmp/pin/sources.list.d";' \
        'Acquire::Check-Valid-Until "false";' \
        'Acquire::https::apt-archive.postgresql.org::CaInfo "/run/apt-archive-ca.pem";' \
        > /tmp/pin/apt.conf; \
    export APT_CONFIG=/tmp/pin/apt.conf DEBIAN_FRONTEND=noninteractive; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        libarpack2t64=3.9.1-6 \
        libblas3=3.12.1-6 \
        libgfortran5=14.2.0-19 \
        liblapack3=3.12.1-6; \
    if [ "$TARGETARCH" = amd64 ]; then \
        apt-get install -y --no-install-recommends libarmadillo14=1:14.2.3+dfsg-1+b1; \
    else \
        apt-get install -y --no-install-recommends libarmadillo14=1:14.2.3+dfsg-1; \
    fi; \
    apt-get install -y --no-install-recommends \
        fontconfig-config=2.15.0-2.3 \
        fonts-dejavu-core=2.37-8 \
        fonts-dejavu-mono=2.37-8 \
        gdal-data=3.13.2+dfsg-1.pgdg13+1 \
        gdal-plugins=3.13.2+dfsg-1.pgdg13+1 \
        libabsl20240722=20240722.0-4 \
        libaec0=1.1.3-1+b1 \
        libaom3=3.12.1-1+deb13u1 \
        libavif16=1.2.1-1.2 \
        libblosc1=1.21.5+ds-1+b2 \
        libboost-serialization1.83.0=1.83.0-4.2 \
        libbrotli1=1.1.0-2+b7 \
        libcfitsio10t64=4.6.2-2 \
        libcurl3t64-gnutls=8.14.1-2+deb13u5 \
        libcurl4t64=8.14.1-2+deb13u5 \
        libdav1d7=1.5.1-1 \
        libde265-0=1.0.15-1+deb13u2 \
        libdeflate0=1.23-2 \
        libexpat1=2.8.3-1~deb13u1 \
        libfontconfig1=2.15.0-2.3 \
        libfreetype6=2.13.3+dfsg-1+deb13u1 \
        libfreexl1=2.0.0-1+b3 \
        libfyba0t64=4.1.1-11+b1 \
        libgav1-1=0.19.0-3+b1 \
        libgdal36=3.10.3+dfsg-1 \
        libgeos3.14.1=3.14.1-2.pgdg13+1 \
        libgeos-c1t64=3.14.1-2.pgdg13+1 \
        libgeotiff5=1.7.4-1 \
        libgif7=5.2.2-1+deb13u1 \
        libgmpxx4ldbl=2:6.3.0+dfsg-3 \
        libgpgme11t64=1.24.2-3 \
        libgpgmepp6t64=1.24.2-3 \
        libhdf4-0-alt=4.3.0-1+b1 \
        libhdf5-310=1.14.5+repack-3 \
        libhdf5-hl-310=1.14.5+repack-3 \
        libheif1=1.19.8-1+deb13u1 \
        libheif-plugin-dav1d=1.19.8-1+deb13u1 \
        libheif-plugin-libde265=1.19.8-1+deb13u1 \
        libjbig0=2.1-6.1+b2 \
        libjpeg62-turbo=1:2.1.5-4 \
        libjson-c5=0.18+ds-1 \
        libkmlbase1t64=1.3.0-12+b2 \
        libkmldom1t64=1.3.0-12+b2 \
        libkmlengine1t64=1.3.0-12+b2 \
        liblcms2-2=2.16-2+deb13u2 \
        liblerc4=4.0.0+ds-5 \
        libltdl7=2.5.4-4 \
        libmariadb3=1:11.8.6-0+deb13u1 \
        libminizip1t64=1:1.3.dfsg+really1.3.1-1+b1 \
        libmpfr6=4.2.2-1 \
        libnetcdf22=1:4.9.3-1 \
        libnghttp2-14=1.64.0-1.1+deb13u1 \
        libnghttp3-9=1.8.0-1 \
        libngtcp2-16=1.11.0-1+deb13u1 \
        libngtcp2-crypto-gnutls8=1.11.0-1+deb13u1 \
        libnspr4=2:4.36-1 \
        libnss3=2:3.110-1+deb13u4 \
        libodbc2=2.3.12-2+deb13u1 \
        libodbcinst2=2.3.12-2+deb13u1 \
        libogdi4.1=4.1.1+ds-5 \
        libopenjp2-7=2.5.3-2.1~deb13u2 \
        libpng16-16t64=1.6.48-1+deb13u5 \
        libpoppler147=25.03.0-5+deb13u4 \
        libproj25=9.8.1-1.pgdg13+1 \
        libprotobuf-c1=1.5.1-1 \
        libpsl5t64=0.21.2-1.1+b1 \
        libqhull-r8.0=2020.2-6+b2 \
        librav1e0.7=0.7.1-9+b2 \
        librtmp1=2.4+20151223.gitfa8646d.1-2+b5 \
        librttopo1=1.1.0-4 \
        libsfcgal2=2.0.0-1 \
        libsharpyuv0=1.5.0-0.1 \
        libsnappy1v5=1.2.2-1 \
        libspatialite8t64=5.1.0-3+b2 \
        libssh2-1t64=1.11.1-1+deb13u2 \
        libsvtav1enc2=2.3.0+dfsg-1 \
        libsz2=1.1.3-1+b1 \
        libtiff6=4.7.0-3+deb13u3 \
        libtirpc3t64=1.3.6+ds-1 \
        libtirpc-common=1.3.6+ds-1 \
        liburiparser1=0.9.8+dfsg-2 \
        libwebp7=1.5.0-0.1 \
        libxerces-c3.2t64=3.2.4+debian-1.3+b2 \
        libyuv0=0.0.1904.20250204-1 \
        mariadb-common=1:11.8.6-0+deb13u1 \
        mysql-common=5.8+1.1.1 \
        postgresql-17-pgvector=0.8.6-1.pgdg13+2 \
        postgresql-17-postgis-3=3.6.4+dfsg-2.pgdg13+1 \
        postgresql-17-postgis-3-scripts=3.6.4+dfsg-2.pgdg13+1 \
        proj-data=9.8.1-1.pgdg13+1 \
        unixodbc-common=2.3.12-2+deb13u1; \
    rm -rf /tmp/pin /var/lib/apt/lists/* /var/cache/apt/*.bin /var/log/apt/* /var/log/dpkg.log /var/cache/ldconfig/aux-cache
