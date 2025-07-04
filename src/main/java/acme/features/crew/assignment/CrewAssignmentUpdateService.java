
package acme.features.crew.assignment;

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
import acme.entities.assignment.Assignment;
import acme.entities.assignment.CurrentStatus;
import acme.entities.assignment.DutyCrew;
import acme.entities.leg.Leg;
import acme.realms.crew.Crew;

@GuiService
public class CrewAssignmentUpdateService extends AbstractGuiService<Crew, Assignment> {

	@Autowired
	private CrewAssignmentRepository repository;


	@Override
	public void authorise() {
		int currentCrewMemberId;
		int assignmentId;
		int legId;
		Leg leg;
		Assignment assignment;
		boolean crewMemberExists;
		boolean assignmentBelongsToCrewMember;
		boolean isAssignmentOwner;
		boolean isAssignmentPublished;
		boolean status;
		boolean status2;
		String method;

		currentCrewMemberId = super.getRequest().getPrincipal().getActiveRealm().getId();
		assignmentId = super.getRequest().getData("id", int.class);
		assignment = this.repository.findAssignmentById(assignmentId);

		crewMemberExists = this.repository.existsCrewMember(currentCrewMemberId);
		assignmentBelongsToCrewMember = crewMemberExists && this.repository.isAssignmentOwnedByCrewMember(assignmentId, currentCrewMemberId);
		isAssignmentOwner = assignment.getCrew().getId() == currentCrewMemberId;
		isAssignmentPublished = assignment.isDraftMode();
		status = assignmentBelongsToCrewMember && isAssignmentOwner && isAssignmentPublished;

		method = super.getRequest().getMethod();

		if ("GET".equals(method))
			status2 = status;
		else {
			// Validación de duty
			String dutyStatus = super.getRequest().getData("duty", String.class);
			if (!"0".equals(dutyStatus))
				status = status && Arrays.stream(DutyCrew.values()).anyMatch(tc -> tc.name().equalsIgnoreCase(dutyStatus));

			// Validación de currentStatus
			String currentStatus = super.getRequest().getData("currentStatus", String.class);
			if (!"0".equals(currentStatus))
				status = status && Arrays.stream(CurrentStatus.values()).anyMatch(tc -> tc.name().equalsIgnoreCase(currentStatus));

			// Validación de leg
			legId = super.getRequest().getData("leg", int.class);
			leg = this.repository.findLegById(legId);
			status2 = (legId == 0 || leg != null) && status;

			// Validación de lastUpdate
			if (status2) {
				Date lastUpdateClient = super.getRequest().getData("lastUpdate", Date.class);
				Date lastUpdateServer = assignment.getLastUpdate();
				if (lastUpdateClient == null || !lastUpdateClient.equals(lastUpdateServer))
					status2 = false;
			}
		}

		super.getResponse().setAuthorised(status2);
	}

	@Override
	public void load() {
		Assignment assignment;
		int assignmentId;

		assignmentId = super.getRequest().getData("id", int.class);
		assignment = this.repository.findAssignmentById(assignmentId);

		super.getBuffer().addData(assignment);
	}

	@Override
	public void bind(final Assignment assignment) {
		Integer legId;
		Leg leg;
		Crew member;
		Integer crewId;

		legId = super.getRequest().getData("leg", int.class);
		leg = this.repository.findLegById(legId);

		crewId = super.getRequest().getPrincipal().getActiveRealm().getId();
		member = this.repository.findCrewById(crewId);

		super.bindObject(assignment, "duty", "currentStatus", "remarks");
		assignment.setLeg(leg);
		assignment.setCrew(member);
	}

	@Override
	public void validate(final Assignment assignment) {
		Assignment original = this.repository.findAssignmentById(assignment.getId());
		Crew crew = assignment.getCrew();
		Leg leg = assignment.getLeg();

		boolean cambioDuty = !original.getDuty().equals(assignment.getDuty());
		boolean cambioLeg = !original.getLeg().equals(assignment.getLeg());
		boolean cambioMoment = !original.getLastUpdate().equals(assignment.getLastUpdate());
		boolean cambioStatus = !original.getCurrentStatus().equals(assignment.getCurrentStatus());

		if (!(cambioDuty || cambioLeg || cambioMoment || cambioStatus))
			return;

		if (crew != null && leg != null && cambioLeg && !this.isLegCompatible(assignment))
			super.state(false, "crew", "acme.validation.assignment.CrewIncompatibleLegs.message");

		if (leg != null && (cambioDuty || cambioLeg))
			this.checkPilotAndCopilotAssignment(assignment);

		if (leg != null && cambioLeg) {
			boolean legCompleted = this.repository.areLegsCompletedByAssignment(assignment.getId(), MomentHelper.getCurrentMoment());
			if (legCompleted)
				super.state(false, "leg", "acme.validation.assignment.LegAlreadyCompleted.message");
		}
	}

	private boolean isLegCompatible(final Assignment assignment) {
		Collection<Leg> legsByCrew = this.repository.findLegsByCrewId(assignment.getCrew().getId());
		Leg newLeg = assignment.getLeg();

		return legsByCrew.stream().allMatch(existingLeg -> this.areLegsCompatible(newLeg, existingLeg));
	}

	private boolean areLegsCompatible(final Leg newLeg, final Leg oldLeg) {
		return !(MomentHelper.isInRange(newLeg.getScheduledDeparture(), oldLeg.getScheduledDeparture(), oldLeg.getScheduledArrival()) || MomentHelper.isInRange(newLeg.getScheduledArrival(), oldLeg.getScheduledDeparture(), oldLeg.getScheduledArrival())
			|| newLeg.getScheduledDeparture().before(oldLeg.getScheduledDeparture()) && newLeg.getScheduledArrival().after(oldLeg.getScheduledArrival()));
	}

	private void checkPilotAndCopilotAssignment(final Assignment assignment) {
		boolean havePilot = this.repository.existsCrewWithDutyInLeg(assignment.getLeg().getId(), DutyCrew.PILOT);
		boolean haveCopilot = this.repository.existsCrewWithDutyInLeg(assignment.getLeg().getId(), DutyCrew.CO_PILOT);

		if (DutyCrew.PILOT.equals(assignment.getDuty()))
			super.state(!havePilot, "duty", "acme.validation.assignment.havePilot.message");

		if (DutyCrew.CO_PILOT.equals(assignment.getDuty()))
			super.state(!haveCopilot, "duty", "acme.validation.assignment.haveCopilot.message");
	}

	@Override
	public void perform(final Assignment assignment) {
		assignment.setLastUpdate(MomentHelper.getCurrentMoment());
		this.repository.save(assignment);
	}

	@Override
	public void unbind(final Assignment assignment) {
		Dataset dataset;
		SelectChoices statuses;
		SelectChoices duties;
		Collection<Leg> legs;
		SelectChoices legChoices;
		boolean isCompleted;
		int assignmentId;
		Date currentMoment;

		assignmentId = super.getRequest().getData("id", int.class);
		currentMoment = MomentHelper.getCurrentMoment();
		isCompleted = this.repository.areLegsCompletedByAssignment(assignmentId, currentMoment);

		legs = this.repository.findAllPublishedLegs();
		legChoices = SelectChoices.from(legs, "flightNumber", assignment.getLeg());

		statuses = SelectChoices.from(CurrentStatus.class, assignment.getCurrentStatus());
		duties = SelectChoices.from(DutyCrew.class, assignment.getDuty());

		int crewMemberId = super.getRequest().getPrincipal().getActiveRealm().getId();
		Crew crewMember = this.repository.findCrewById(crewMemberId);
		Collection<Crew> crewMembers = assignment.getLeg() != null ? this.repository.findCrewMembersByLegId(assignment.getLeg().getId()) //
			: List.of(); //

		dataset = super.unbindObject(assignment, "duty", "lastUpdate", "currentStatus", "remarks", "draftMode");
		dataset.put("readonly", !assignment.isDraftMode());
		dataset.put("lastUpdate", assignment.getLastUpdate());
		dataset.put("currentStatus", statuses);
		dataset.put("duty", duties);

		if (assignment.getLeg() != null && legChoices.getSelected() != null)
			dataset.put("leg", legChoices.getSelected().getKey());
		else
			dataset.put("leg", "0");
		dataset.put("leg", legChoices.getSelected().getKey());
		dataset.put("legs", legChoices);
		dataset.put("crewMember", crewMember.getCode());
		dataset.put("isCompleted", isCompleted);
		dataset.put("draftMode", assignment.isDraftMode());

		String crewCodes = crewMembers.stream().map(Crew::getCode).distinct().reduce((a, b) -> a + ", " + b).orElse("-");

		dataset.put("crewMembers", crewCodes);

		super.getResponse().addData(dataset);
	}

}
