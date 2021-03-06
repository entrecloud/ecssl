package com.entrecloud.ecssl.acme;

import com.entrecloud.ecssl.configuration.Configuration;
import com.entrecloud.ecssl.configuration.Option;
import org.shredzone.acme4j.Registration;
import org.shredzone.acme4j.RegistrationBuilder;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.exception.AcmeConflictException;
import org.shredzone.acme4j.exception.AcmeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ParametersAreNonnullByDefault;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ParametersAreNonnullByDefault
public class EnsureRegistration {
    private final static Logger logger = LoggerFactory.getLogger(EnsureRegistration.class);

    public Registration registration(Configuration configuration, Session session) {
        String email = configuration.getOption("account-email").getValueAsString();

        Registration reg;
        try {
            RegistrationBuilder registrationBuilder = new RegistrationBuilder();
            registrationBuilder.addContact("mailto:" + email);
            reg = registrationBuilder.create(session);
            logger.trace("Registered new ACME account.");
        } catch (AcmeConflictException ex) {
            reg = Registration.bind(session, ex.getLocation());
            List<String> registeredEmails = reg
                .getContacts()
                .stream()
                .map(URI::toString)
                .map(registeredEmail -> registeredEmail.replaceAll("\\Amailto:", ""))
                .collect(Collectors.toList());
            if (!registeredEmails.contains(email)) {
                try {
                    logger.trace("Updating ACME account, adding e-mail: " + email);
                    reg.modify().addContact("mailto:" + email).commit();
                } catch (AcmeException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (AcmeException e) {
            logger.warn("Exception while processing ACME account: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }

        try {
            String termsUrl = configuration.getOption("agree-terms-url").getValueAsString();
            URI agreementUri = reg.getAgreement();
            if (!agreementUri.toString().equals(termsUrl)) {
                throw new RuntimeException("Invalid terms URL: " + termsUrl + " you must accept " + agreementUri.toString());
            } else {
                try {
                    logger.trace("Accepting agreement at " + agreementUri.toString());
                    reg.modify().setAgreement(agreementUri).commit();
                } catch (AcmeException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (Option.ConfigurationOptionNotSet e) {
        }

        return reg;
    }
}
