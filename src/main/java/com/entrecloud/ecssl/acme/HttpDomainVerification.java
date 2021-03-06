package com.entrecloud.ecssl.acme;

import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Registration;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Challenge;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.exception.AcmeUnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@ParametersAreNonnullByDefault
public class HttpDomainVerification {
    private final Logger logger = LoggerFactory.getLogger(HttpDomainVerification.class);

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
                        logger.warn("Challenge is invalid, aborting.");
                        return false;
                    }

                    // Wait for a few seconds
                    Thread.sleep(1000L);

                    logger.trace("Updating challenge...");
                    // Then update the status
                    challenge.update();
                }
                if (attempts == 0 && challenge.getStatus() != Status.VALID) {
                    logger.trace("All challenges exhausted, aborting.");
                    return false;
                }
            } catch (InterruptedException ex) {
                logger.trace("Challenge interrupted, aborting.");
                return false;
            }
            return challenge.getStatus() == Status.VALID;
        } catch (AcmeException e) {
            logger.trace("Validation exception: " + e.getMessage(), e);
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
                    logger.trace("Creating challenge file: " + challengeFile.toString());
                    challengeFile.createNewFile();
                    PrintWriter out = new PrintWriter(new FileWriter(challengeFile));
                    out.print(httpChallenge.getAuthorization());
                    out.close();

                    if (!executeChallenge(httpChallenge)) {
                        throw new RuntimeException("HTTP challenge failed for " + domain);
                    }

                } catch (IOException e) {
                    logger.trace("IO exception while processing validation: " + e.getMessage(), e);
                    throw new RuntimeException(e);
                } finally {
                    try {
                        challengeFile.delete();
                        delete(webroot + "/.well-known/acme-challenge");
                        delete(webroot + "/.well-known");
                    } catch (Throwable e) {
                        //If we can't remove it, so be it.
                        logger.trace("Could not delete challenge file or directory at " + challengeFile.toString(), e);
                    }
                }
            }
        } catch (AcmeUnauthorizedException e) {
            logger.warn("Unauthorized, you probably have to accept the terms at " + registration.getAgreement());
            throw new RuntimeException("Unauthorized, you probably have to accept the terms at " + registration.getAgreement(), e);
        } catch (AcmeException e) {
            throw new RuntimeException(e);
        }
    }
}
