package dev.phoenix.error;

/** A referenced job, ask or other record does not exist. */
public class NotFoundException extends UserErrorException {

    public NotFoundException(String what, long id) {
        super(what + " " + id + " not found");
    }
}
