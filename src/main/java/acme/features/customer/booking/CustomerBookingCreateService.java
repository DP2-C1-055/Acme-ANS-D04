
package acme.features.customer.booking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import acme.client.components.models.Dataset;
import acme.client.components.views.SelectChoices;
import acme.client.helpers.MomentHelper;
import acme.client.services.AbstractGuiService;
import acme.client.services.GuiService;
import acme.entities.booking.Booking;
import acme.entities.booking.TravelClass;
import acme.entities.flight.Flight;
import acme.realms.Customer.Customer;

@GuiService
public class CustomerBookingCreateService extends AbstractGuiService<Customer, Booking> {

	@Autowired
	private CustomerBookingRepository repository;


	@Override
	public void authorise() {
		boolean status;

		status = super.getRequest().getPrincipal().hasRealmOfType(Customer.class);

		if (super.getRequest().getMethod().equals("POST")) {
			String travelClass = super.getRequest().getData("travelClass", String.class);
			if (!travelClass.equals("0"))
				status = Arrays.stream(TravelClass.values()).anyMatch(tc -> tc.name().equalsIgnoreCase(travelClass));

			int flightId = super.getRequest().getData("flight", int.class);
			Flight flight = this.repository.getFlightById(flightId);
			if (flightId != 0 && flight == null)
				status = false;
			if (flight != null)
				if (flight.isDraftMode())
					status = false;
				else if (!flight.getScheduledDeparture().after(MomentHelper.getCurrentMoment()))
					status = false;
		}

		super.getResponse().setAuthorised(status);
	}

	@Override
	public void load() {

		Booking booking = new Booking();
		booking.setLocatorCode("");
		booking.setLastNibble("");

		int customerId;
		customerId = super.getRequest().getPrincipal().getActiveRealm().getId();
		Customer customer = this.repository.getCustomerById(customerId);
		booking.setCustomer(customer);

		Date currentMoment = MomentHelper.getCurrentMoment();
		booking.setPurchaseMoment(currentMoment);

		super.getBuffer().addData(booking);
	}

	@Override
	public void validate(final Booking object) {
		Collection<String> allLocatorCode = this.repository.getAllLocatorCode();

		if (!super.getBuffer().getErrors().hasErrors("locatorCode"))
			super.state(!allLocatorCode.contains(object.getLocatorCode()), "locatorCode", "customer.booking.error.locatorCodeDuplicate");
	}

	@Override
	public void perform(final Booking booking) {
		booking.setDraftMode(true);

		this.repository.save(booking);
	}

	@Override
	public void unbind(final Booking object) {
		assert object != null;

		Dataset dataset;

		SelectChoices choices;
		List<Flight> flightsInTheFuture = new ArrayList<>();
		Collection<Flight> flights = this.repository.getAllFlightWithDraftModeFalse();
		Date currentMoment = MomentHelper.getCurrentMoment();
		for (Flight flight : flights)
			if (flight.getScheduledDeparture().after(currentMoment))
				flightsInTheFuture.add(flight);
		if (object.getFlight() != null)
			choices = SelectChoices.from(flightsInTheFuture, "customFlightText", object.getFlight());
		else
			choices = SelectChoices.from(flightsInTheFuture, "customFlightText", null);

		dataset = super.unbindObject(object, "locatorCode", "purchaseMoment", "travelClass", "lastNibble", "draftMode");
		dataset.put("travelClassChoices", SelectChoices.from(TravelClass.class, object.getTravelClass()));
		dataset.put("flight", choices.getSelected().getKey());
		dataset.put("flights", choices);

		super.getResponse().addData(dataset);

	}

	@Override
	public void bind(final Booking object) {
		assert object != null;

		int flightId;
		Flight flight;

		flightId = super.getRequest().getData("flight", int.class);
		flight = this.repository.getFlightById(flightId);

		super.bindObject(object, "locatorCode", "travelClass", "lastNibble");
		object.setFlight(flight);
	}

}
