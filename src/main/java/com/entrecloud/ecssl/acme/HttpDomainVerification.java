package com.entrecloud.ecssl.acme;

import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Registration;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@ParametersAreNonnullByDefault
public class HttpDomainVerification {
    private final String domain;
    private final Registration registration;
    private final String webroot;

    public HttpDomainVerification(
        String domain,
        Registration registration,
        String webroot
    ) {
        this.domain = domain;
        this.registration = registration;
        this.webroot = webroot;
    }

    private void delete(String directory) {
        File file = new File(directory);
        if (file.list().length == 0) {
            file.delete();
        }
    }

    private boolean executeChallenge(Challenge challenge) {
        try {
            challenge.trigger();
            try {
                int attempts = 10;
                while (challenge.getStatus() != Status.VALID && attempts-- > 0) {
                    if (challenge.getStatus() == Status.INVALID) {
                        return false;
                    }

                    // Wait for a few seconds
                    Thread.sleep(1000L);

                    // Then update the status
                    challenge.update();
                }
                if (attempts == 0 && challenge.getStatus() != Status.VALID) {
                    return false;
                }
            } catch (InterruptedException ex) {
                return false;
            }
            return challenge.getStatus() == Status.VALID;
        } catch (AcmeException e) {
            return false;
        }
    }

    public void run() {
        try {
            Authorization auth = registration.authorizeDomain(domain);
            Http01Challenge httpChallenge = auth.findChallenge(Http01Challenge.TYPE);
            if (httpChallenge.getStatus() != Status.VALID) {

                new File(webroot + "/.well-known/acme-challenge").mkdirs();
                File challengeFile = new File(webroot + "/.well-known/acme-challenge/" + httpChallenge.getToken());
                try {
                    challengeFile.createNewFile();
                    PrintWriter out = new PrintWriter(new FileWriter(challengeFile));
                    out.print(httpChallenge.getAuthorization());
                    out.close();

                    if (!executeChallenge(httpChallenge)) {
                        throw new RuntimeException("HTTP challenge failed for " + domain);
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    challengeFile.delete();
                    delete(webroot + "/.well-known/acme-challenge");
                    delete(webroot + "/.well-known");
                }
            }
        } catch (AcmeException e) {
            throw new RuntimeException(e);
        }
    }
}