
package acme.constraints;

import javax.validation.ConstraintValidatorContext;

import acme.client.components.principals.DefaultUserIdentity;
import acme.client.components.validation.AbstractValidator;
import acme.realms.crew.Crew;

public class CrewValidator extends AbstractValidator<ValidCrewIdentifier, Crew> {

	// Internal state ---------------------------------------------------------

	@Override
	protected void initialise(final ValidCrewIdentifier annotation) {
		assert annotation != null;
	}

	@Override
	public boolean isValid(final Crew crew, final ConstraintValidatorContext context) {
		boolean result;

		assert context != null;

		if (crew == null)
			super.state(context, false, "*", "javax.validation.constraints.NotNull.message");
		else {
			String identifier = crew.getCode();
			boolean matchesPattern = identifier != null && identifier.matches("^[A-Z]{2,3}\\d{6}$");

			if (matchesPattern) {
				DefaultUserIdentity dui = crew.getIdentity();
				boolean check = dui.getName().charAt(0) == identifier.charAt(0) && dui.getSurname().charAt(0) == identifier.charAt(1);
				super.state(context, check, "identifier", "acme.validation.crew.invalid-identifier.message");
			} else
				super.state(context, false, "identifier", "acme.validation.crew.invalid-identifier.message");
		}

		result = !super.hasErrors(context);
		return result;
	}
}
