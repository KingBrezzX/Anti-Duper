package xyz.zyrex.bedrockantidupe;

import java.util.UUID;

/**
 * Result of an economy rollback operation.
 *
 * SUCCESS  = complete recorded amount was rolled back.
 * PARTIAL  = only part of the recorded amount could be rolled back.
 * FAILED   = nothing was rolled back.
 */
public record RollbackResult(

        UUID transactionId,

        Status status,

        double requestedAmount,

        double rolledBackAmount,

        String message

) {

    public enum Status {
        SUCCESS,
        PARTIAL,
        FAILED
    }

    public RollbackResult {

        if (transactionId == null) {
            throw new IllegalArgumentException(
                    "transactionId cannot be null"
            );
        }

        if (status == null) {
            status = Status.FAILED;
        }

        if (requestedAmount < 0) {
            requestedAmount = 0;
        }

        if (rolledBackAmount < 0) {
            rolledBackAmount = 0;
        }

        if (message == null) {
            message = "";
        }
    }

    /**
     * Creates a successful rollback result.
     */
    public static RollbackResult success(
            UUID transactionId,
            double requestedAmount,
            double rolledBackAmount
    ) {

        return new RollbackResult(
                transactionId,
                Status.SUCCESS,
                requestedAmount,
                rolledBackAmount,
                "Economy rollback completed."
        );
    }

    /**
     * Creates a partial rollback result.
     */
    public static RollbackResult partial(
            UUID transactionId,
            double requestedAmount,
            double rolledBackAmount,
            String message
    ) {

        return new RollbackResult(
                transactionId,
                Status.PARTIAL,
                requestedAmount,
                rolledBackAmount,
                message
        );
    }

    /**
     * Creates a failed rollback result.
     */
    public static RollbackResult failed(
            UUID transactionId,
            double requestedAmount,
            double rolledBackAmount,
            String message
    ) {

        return new RollbackResult(
                transactionId,
                Status.FAILED,
                requestedAmount,
                rolledBackAmount,
                message
        );
    }

    public boolean isSuccess() {

        return status == Status.SUCCESS;
    }

    public boolean isPartial() {

        return status == Status.PARTIAL;
    }

    public boolean isFailed() {

        return status == Status.FAILED;
    }

    public boolean changedEconomy() {

        return rolledBackAmount > 0;
    }

    public double remainingAmount() {

        return Math.max(
                0.0,
                requestedAmount - rolledBackAmount
        );
    }
      }
