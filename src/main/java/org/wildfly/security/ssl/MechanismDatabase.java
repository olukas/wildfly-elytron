/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.security.ssl;

import static org.wildfly.security._private.ElytronMessages.log;

import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class MechanismDatabase {
    private static final MechanismDatabase INSTANCE = new MechanismDatabase();

    private final Map<String, Entry> entriesByStdName;
    private final Map<String, Entry> entriesByOSSLName;

    static MechanismDatabase getInstance() {
        return INSTANCE;
    }

    MechanismDatabase() {
        // load and initialize database properties
        final LinkedProperties properties = new LinkedProperties();

        try (InputStream stream = getClass().getResourceAsStream("MechanismDatabase.properties")) {
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            throw new IOError(e);
        }

        Pattern p = Pattern.compile("\\s*,\\s*");
        final Map<String, Entry> entriesByStdName = new LinkedHashMap<>();
        final Map<String, Entry> entriesByOSSLName = new LinkedHashMap<>();
        final Map<KeyAgreement, List<Entry>> entriesByKeyExchange = new EnumMap<KeyAgreement, List<Entry>>(KeyAgreement.class);
        final Map<Authentication, List<Entry>> entriesByAuthentication = new EnumMap<Authentication, List<Entry>>(Authentication.class);
        final Map<Encryption, List<Entry>> entriesByEncryption = new EnumMap<Encryption, List<Entry>>(Encryption.class);
        final Map<Digest, List<Entry>> entriesByDigest = new EnumMap<Digest, List<Entry>>(Digest.class);
        final Map<Protocol, List<Entry>> entriesByProtocol = new EnumMap<Protocol, List<Entry>>(Protocol.class);
        final Map<SecurityLevel, List<Entry>> entriesByLevel = new EnumMap<SecurityLevel, List<Entry>>(SecurityLevel.class);

        final Map<String, String> aliases = new HashMap<>();

        for (Map.Entry<String, String> mapEntry : properties.stringMapEntries()) {
            final String name = mapEntry.getKey();
            final String rawValue = mapEntry.getValue();
            final String[] strings = p.split(rawValue);
            if (strings.length == 1 && strings[0].startsWith("alias:")) {
                aliases.put(name, strings[0].substring(6));
            } else if (strings.length != 11) {
                log.warnInvalidStringCountForMechanismDatabaseEntry(name);
            } else {
                boolean ok = true;
                final String openSslName = strings[0];
                final KeyAgreement kex = KeyAgreement.forName(strings[1]);
                if (kex == null) {
                    log.warnInvalidKeyExchangeForMechanismDatabaseEntry(strings[1], name);
                    ok = false;
                }
                final Authentication auth = Authentication.forName(strings[2]);
                if (auth == null) {
                    log.warnInvalidAuthenticationForMechanismDatabaseEntry(strings[2], name);
                    ok = false;
                }
                final Encryption enc = Encryption.forName(strings[3]);
                if (enc == null) {
                    log.warnInvalidEncryptionForMechanismDatabaseEntry(strings[3], name);
                    ok = false;
                }
                final Digest digest = Digest.forName(strings[4]);
                if (digest == null) {
                    log.warnInvalidDigestForMechanismDatabaseEntry(strings[4], name);
                    ok = false;
                }
                final Protocol prot = Protocol.forName(strings[5]);
                if (prot == null) {
                    log.warnInvalidProtocolForMechanismDatabaseEntry(strings[5], name);
                    ok = false;
                }
                final boolean export = Boolean.parseBoolean(strings[6]);
                final SecurityLevel level = SecurityLevel.forName(strings[7]);
                if (level == null) {
                    log.warnInvalidLevelForMechanismDatabaseEntry(strings[7], name);
                    ok = false;
                }
                final boolean fips = Boolean.parseBoolean(strings[8]);
                int strBits;
                try {
                    strBits = Integer.parseInt(strings[9], 10);
                } catch (NumberFormatException ignored) {
                    log.warnInvalidStrengthBitsForMechanismDatabaseEntry(strings[9], name);
                    strBits = 0;
                    ok = false;
                }
                int algBits;
                try {
                    algBits = Integer.parseInt(strings[10], 10);
                } catch (NumberFormatException ignored) {
                    log.warnInvalidAlgorithmBitsForMechanismDatabaseEntry(strings[10], name);
                    algBits = 0;
                    ok = false;
                }
                if (ok) {
                    final Entry entry = new Entry(name, openSslName, new ArrayList<String>(0), kex, auth, enc, digest, prot, export, level, fips, strBits, algBits);
                    if (entriesByStdName.containsKey(name)) {
                        log.warnInvalidDuplicateMechanismDatabaseEntry(name);
                    } else {
                        entriesByStdName.put(name, entry);
                    }
                    if (entriesByOSSLName.containsKey(openSslName)) {
                        log.warnInvalidDuplicateOpenSslStyleAliasForMechanismDatabaseEntry(openSslName, name, entriesByOSSLName.get(openSslName).getName());
                    } else {
                        entriesByOSSLName.put(openSslName, entry);
                    }
                    if (kex == KeyAgreement.DHE) {
                        final String newKey = join("-", replaceEdh(openSslName.split("-")));
                        if (!entriesByOSSLName.containsKey(newKey)) {
                            entriesByOSSLName.put(newKey, entry);
                        }
                    }
                    addTo(entriesByKeyExchange, kex, entry);
                    addTo(entriesByAuthentication, auth, entry);
                    addTo(entriesByEncryption, enc, entry);
                    addTo(entriesByDigest, digest, entry);
                    addTo(entriesByProtocol, prot, entry);
                    addTo(entriesByLevel, level, entry);
                }
            }
        }

        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            final String name = entry.getKey();
            String value = entry.getValue();

            while (aliases.containsKey(value)) {
                // Just skip to the end, intermediate aliases will get their own turn in the for loop.
                value = aliases.get(value);
            }

            if (entriesByStdName.containsKey(name)) {
                log.warnInvalidDuplicateMechanismDatabaseEntry(name);
            } else if (! entriesByStdName.containsKey(value)) {
                log.warnInvalidAliasForMissingMechanismDatabaseEntry(value, name);
            } else {
                final Entry dbEntry = entriesByStdName.get(value);
                dbEntry.getAliases().add(name);
                entriesByStdName.put(name, dbEntry);
            }
        }

        this.entriesByStdName = entriesByStdName;
        this.entriesByOSSLName = entriesByOSSLName;
    }

    static String[] replaceEdh(String... strings) {
        for (int i = 0, stringsLength = strings.length; i < stringsLength; i++) {
            if ("EDH".equals(strings[i])) {
                strings[i] = "DHE";
            } else if ("DHE".equals(strings[i])) {
                strings[i] = "EDH";
            }
        }
        return strings;
    }

    static String join(String joiner, String... strings) {
        StringBuilder b = new StringBuilder();
        final int length = strings.length;
        int i = 0;
        if (length == 0) return "";
        for (;;) {
            b.append(strings[i ++]);
            if (i == length) {
                return b.toString();
            }
            b.append(joiner);
        }
    }

    static <T> void addTo(Map<T, List<Entry>> map, T item, Entry entry) {
        List<Entry> list = map.get(item);
        if (list == null) {
            list = new ArrayList<>(1);
            map.put(item, list);
        }
        list.add(entry);
    }

    Entry getCipherSuite(final String cipherSuite) {
        return entriesByStdName.get(cipherSuite);
    }

    Entry getCipherSuiteOpenSSLName(final String cipherSuite) {
        return entriesByOSSLName.get(cipherSuite);
    }

    static final class Entry {
        private final String name;
        private final String openSslName;
        private final List<String> aliases;
        private final KeyAgreement keyAgreement;
        private final Authentication authentication;
        private final Encryption encryption;
        private final Digest digest;
        private final Protocol protocol;
        private final boolean export;
        private final SecurityLevel level;
        private final boolean fips;
        private final int strengthBits;
        private final int algorithmBits;

        Entry(final String name, final String openSslName, final List<String> aliases, final KeyAgreement keyAgreement, final Authentication authentication, final Encryption encryption, final Digest digest, final Protocol protocol, final boolean export, final SecurityLevel level, final boolean fips, final int strengthBits, final int algorithmBits) {
            this.name = name;
            this.openSslName = openSslName;
            this.aliases = aliases;
            this.keyAgreement = keyAgreement;
            this.authentication = authentication;
            this.encryption = encryption;
            this.digest = digest;
            this.protocol = protocol;
            this.export = export;
            this.level = level;
            this.fips = fips;
            this.strengthBits = strengthBits;
            this.algorithmBits = algorithmBits;
        }

        public String getName() {
            return name;
        }

        public String getOpenSslName() {
            return openSslName;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public KeyAgreement getKeyAgreement() {
            return keyAgreement;
        }

        public Authentication getAuthentication() {
            return authentication;
        }

        public Encryption getEncryption() {
            return encryption;
        }

        public Digest getDigest() {
            return digest;
        }

        public Protocol getProtocol() {
            return protocol;
        }

        public boolean isExport() {
            return export;
        }

        public SecurityLevel getLevel() {
            return level;
        }

        public boolean isFips() {
            return fips;
        }

        public int getStrengthBits() {
            return strengthBits;
        }

        public int getAlgorithmBits() {
            return algorithmBits;
        }

        public String toString() {
            return getName() + "/" + getOpenSslName();
        }
    }
}
