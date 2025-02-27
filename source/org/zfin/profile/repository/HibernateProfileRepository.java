package org.zfin.profile.repository;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.hibernate.transform.BasicTransformerAdapter;
import org.springframework.stereotype.Repository;
import org.zfin.framework.HibernateUtil;
import org.zfin.framework.presentation.PaginationResult;
import org.zfin.infrastructure.InfrastructureService;
import org.zfin.infrastructure.Updates;
import org.zfin.marker.Marker;
import org.zfin.marker.presentation.OrganizationLink;
import org.zfin.profile.*;
import org.zfin.profile.presentation.*;
import org.zfin.profile.service.ProfileService;
import org.zfin.publication.Publication;
import org.zfin.publication.repository.PublicationRepository;
import org.zfin.repository.PaginationResultFactory;
import org.zfin.repository.RepositoryFactory;

import javax.persistence.Tuple;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.zfin.framework.HibernateUtil.currentSession;

/**
 * Persistence storage of profile data.
 */
@Repository
public class HibernateProfileRepository implements ProfileRepository {

	private final Logger logger = LogManager.getLogger(HibernateProfileRepository.class);

	public Person getPerson(String zdbID) {
		if (zdbID == null) {
			return null;
		}
		return HibernateUtil.currentSession().get(Person.class, zdbID);
	}

	public Organization getOrganizationByName(String name) {
		Session session = HibernateUtil.currentSession();
		String hql = "select distinct o  from  Organization o" +
			"     where o.name = :name";
		Query<Organization> query = session.createQuery(hql, Organization.class);
		query.setParameter("name", name);
		return query.uniqueResult();
	}


	public void insertPerson(Person person) {
		Session session = HibernateUtil.currentSession();
		session.save(person);
	}


	public void addSupplier(Organization organization, Marker marker) {
		Session session = HibernateUtil.currentSession();
		MarkerSupplier supplier = new MarkerSupplier();
		supplier.setOrganization(organization);
		supplier.setMarker(marker);
		session.save(supplier);

		InfrastructureService.insertUpdate(marker, "Added supplier " + organization.getName());
	}

	public void removeSupplier(Organization organization, Marker marker) {
		Session session = HibernateUtil.currentSession();
		MarkerSupplier supplier = getSpecificSupplier(marker, organization);
		if (supplier != null) {
			session.delete(supplier);
			InfrastructureService.insertUpdate(marker, "Removed supplier " + organization.getName());
		}
	}

	public void insertLab(Lab lab) {
		Session session = HibernateUtil.currentSession();
		session.save(lab);
	}

	public MarkerSupplier getSpecificSupplier(Marker marker, Organization organization) {
		Session session = currentSession();
		String hql = """
				from MarkerSupplier where marker = :marker and organization = :org
			""";
		Query<MarkerSupplier> query = session.createQuery(hql, MarkerSupplier.class);
		query.setParameter("marker", marker);
		query.setParameter("org", organization);
		return query.uniqueResult();
	}

	public CuratorSession getCuratorSession(String curatorZdbID, String pubZdbID, String field) {
		Session session = HibernateUtil.currentSession();
		String hql = "from CuratorSession where curator.zdbID = :curID AND field = :field ";
		if (pubZdbID != null) {
			hql += " AND publication.zdbID = :pubID";
		}
		Query<CuratorSession> query = session.createQuery(hql, CuratorSession.class);
		query.setParameter("curID", curatorZdbID);
		query.setParameter("field", field);
		if (pubZdbID != null) {
			query.setParameter("pubID", pubZdbID);
		}

		return query.uniqueResult();
	}

	public CuratorSession getCuratorSession(String pubID, CuratorSession.Attribute field) {
		Person curator = ProfileService.getCurrentSecurityUser();
		if (curator == null) {
			return null;
		}

		Session session = HibernateUtil.currentSession();
		Criteria criteria = session.createCriteria(CuratorSession.class);
		criteria.add(Restrictions.eq("curator.zdbID", curator.getZdbID()));
		criteria.add(Restrictions.eq("publication.zdbID", pubID));
		criteria.add(Restrictions.eq("field", field.toString()));
		return (CuratorSession) criteria.uniqueResult();
	}

	public CuratorSession createCuratorSession(String curatorZdbID, String pubZdbID, String field, String value) {
		PublicationRepository publicationRepository = RepositoryFactory.getPublicationRepository();
		Session session = HibernateUtil.currentSession();

		CuratorSession cs = new CuratorSession();

		cs.setCurator(getPerson(curatorZdbID));
		if (pubZdbID != null) {
			cs.setPublication(publicationRepository.getPublication(pubZdbID));
		}
		cs.setField(field);
		cs.setValue(value);

		session.save(cs);
		return cs;
	}

	public void deleteAccountInfo(Person person) {
		AccountInfo accountInfo = person.getAccountInfo();
		if (accountInfo == null) {
			return;
		}

		Session session = HibernateUtil.currentSession();
		AccountInfo info = session.get(AccountInfo.class, person.getZdbID());
		session.delete(info);
	}

	public boolean userExists(String login) {
		Session session = HibernateUtil.currentSession();
		Criteria crit = session.createCriteria(Person.class);
		crit.add(Restrictions.eq("accountInfo.login", login));
		Person person = (Person) crit.uniqueResult();
		return person != null;
	}

	public void updateAccountInfo(Person currentPerson, AccountInfo newAccountInfo) {
		AccountInfo currentAccountInfo = currentPerson.getAccountInfo();
		if (currentAccountInfo == null) {
			return;
		}

		if (newAccountInfo == null) {
			return;
		}

		Session session = HibernateUtil.currentSession();
		String newName = newAccountInfo.getName();
		Person submittingPerson = ProfileService.getCurrentSecurityUser();
		if (StringUtils.isNotEmpty(newName) && !newName.equals(currentAccountInfo.getName())) {
			Updates update = new Updates();
			update.setFieldName("name");
			update.setNewValue(newName);
			update.setOldValue(currentAccountInfo.getName());
			update.setRecID(currentPerson.getZdbID());
			update.setSubmitter(submittingPerson);
			if (submittingPerson != null) {
				update.setSubmitterName(submittingPerson.getUsername());
			}
			update.setWhenUpdated(new Date());
			session.save(update);
			currentAccountInfo.setName(newName);
		}
		// since the password has to be typed in every time there is no concept
		// of changing a password. So we mark it as changed...
		{
			Updates update = new Updates();
			update.setFieldName("password");
			update.setRecID(currentPerson.getZdbID());
			update.setSubmitter(submittingPerson);
			if (submittingPerson != null) {
				update.setSubmitterName(submittingPerson.getUsername());
			}
			update.setWhenUpdated(new Date());
			session.save(update);
		}
		String role = newAccountInfo.getRole();
		if (StringUtils.isNotEmpty(role) && !role.equals(currentAccountInfo.getRole())) {
			Updates update = new Updates();
			update.setFieldName("role");
			update.setRecID(currentPerson.getZdbID());
			update.setNewValue(role);
			update.setOldValue(currentAccountInfo.getRole());
			update.setSubmitter(submittingPerson);
			if (submittingPerson != null)
				update.setSubmitterName(submittingPerson.getUsername());
			update.setWhenUpdated(new Date());
			currentAccountInfo.setRole(role);
			session.save(update);
		}
		String login = newAccountInfo.getLogin();
		if (StringUtils.isNotEmpty(login) && !login.equals(currentAccountInfo.getLogin())) {
			Updates update = new Updates();
			update.setFieldName("login");
			update.setRecID(currentPerson.getZdbID());
			update.setNewValue(login);
			update.setOldValue(currentAccountInfo.getLogin());
			update.setSubmitter(submittingPerson);
			if (submittingPerson != null)
				update.setSubmitterName(submittingPerson.getUsername());
			update.setWhenUpdated(new Date());
			currentAccountInfo.setLogin(login);
			session.save(update);
		}
		session.update(currentPerson);
	}

	/**
	 * Persist curator session info
	 *
	 * @param pubID       pub ID
	 * @param showSection attribute name
	 * @param visibility  attribute value
	 */
	public void setCuratorSession(String pubID, CuratorSession.Attribute showSection, boolean visibility) {
		Session session = HibernateUtil.currentSession();
		CuratorSession curationAttribute = getCuratorSession(pubID, showSection);
		if (curationAttribute != null) {
			curationAttribute.setValue(String.valueOf(visibility));
		} else {
			Person curator = ProfileService.getCurrentSecurityUser();
			// ToDo: IS this the right thing to do?
			if (curator == null) {
				return;
			}

			Publication pub = RepositoryFactory.getPublicationRepository().getPublication(pubID);
			curationAttribute = new CuratorSession();
			curationAttribute.setPublication(pub);
			curationAttribute.setCurator(curator);
			curationAttribute.setField(showSection.toString());
			curationAttribute.setValue(String.valueOf(visibility));
			session.save(curationAttribute);
		}
	}

	/**
	 * Persist curation attribute.
	 *
	 * @param publicationID pub ID
	 * @param attributeName attribute name
	 * @param zdbID         zdbID
	 */
	public void setCuratorSession(String publicationID, CuratorSession.Attribute attributeName, String zdbID) {
		Session session = HibernateUtil.currentSession();
		CuratorSession curationAttribute = getCuratorSession(publicationID, attributeName);
		if (curationAttribute != null) {
			curationAttribute.setValue(zdbID);
		} else {
			Person curator = ProfileService.getCurrentSecurityUser();
			// ToDo: IS this the right thing to do?
			if (curator == null) {
				return;
			}

			Publication pub = RepositoryFactory.getPublicationRepository().getPublication(publicationID);
			curationAttribute = new CuratorSession();
			curationAttribute.setPublication(pub);
			curationAttribute.setCurator(curator);
			curationAttribute.setField(attributeName.toString());
			curationAttribute.setValue(zdbID);
			session.save(curationAttribute);
		}
	}

	public Person getPersonByEmail(String email) {
		return (Person) HibernateUtil.currentSession()
			.createCriteria(Person.class)
			.add(Restrictions.eq("email", email)).uniqueResult();
	}


	/**
	 * Retrieve a person record by login name.
	 *
	 * @param login login
	 * @return person
	 */
	public Person getPersonByName(String login) {
		Session session = HibernateUtil.currentSession();
		Criteria criteria = session.createCriteria(Person.class);
		criteria.add(Restrictions.eq("accountInfo.login", login));
		return (Person) criteria.uniqueResult();
	}

	/*
	 * Gets person records by fullName, "Westerfield, Monte"
	 *
	 * Since it requires an equals match, it will only return
	 * more than one person for non-unique full names, but
	 * the interface needs to handle that in some way.
	 *
	 * */
	public List<Person> getPeopleByFullName(String fullName) {
		Session session = HibernateUtil.currentSession();
		Query<Person> query = session.createQuery("from Person where fullName = :fullName", Person.class);
		query.setParameter("fullName", fullName);
		return query.list();
	}

	/**
	 * Delete a curator session element.
	 *
	 * @param curatorSession CuratorSession
	 */
	public void deleteCuratorSession(CuratorSession curatorSession) {
		Session session = HibernateUtil.currentSession();
		session.delete(curatorSession);
	}

	/**
	 * Retrieve curator session.
	 *
	 * @param publicationID    publication
	 * @param boxDivID         div element
	 * @param mutantDisplayBox attribute
	 * @return curator session
	 */
	public CuratorSession getCuratorSession(String publicationID, String boxDivID, CuratorSession.Attribute mutantDisplayBox) {
		Session session = HibernateUtil.currentSession();
		Person curator = ProfileService.getCurrentSecurityUser();
		String hql = "from CuratorSession where publication.zdbID = :publicationID " +
			"AND field = :fieldName AND curator = :person";
		Query<CuratorSession> query = session.createQuery(hql, CuratorSession.class);
		query.setParameter("publicationID", publicationID);
		query.setParameter("fieldName", boxDivID);
		query.setParameter("person", curator);
		return query.uniqueResult();
	}

	public List<Organization> getOrganizationsByName(String name) {
		Query<Organization> query = HibernateUtil.currentSession().createQuery("from Organization where lower(name) like :name", Organization.class);
		query.setParameter("name", "%" + name.toLowerCase() + "%");
		List<Organization> labs = query.getResultList();
		Collections.sort(labs);
		return labs;
	}


	@Override
	public Lab getLabById(String labZdbId) {
		return HibernateUtil.currentSession().get(Lab.class, labZdbId);
	}

	@Override
	public Lab getLabByName(String name) {
		return HibernateUtil.currentSession().createQuery("from Lab where name = :name", Lab.class)
			.setParameter("name", name).uniqueResult();
	}

	@Override
	public List<OrganizationLink> getSupplierLinksForZdbId(String zdbID) {
		String sql = """
						SELECT id.idsup_supplier_zdb_id,
						       su.srcurl_url,
						       su.srcurl_display_text,
						       id.idsup_acc_num,
						       comp.NAME AS cname,
						       l.NAME    AS lname
						FROM   int_data_supplier id
						       LEFT OUTER JOIN source_url su
						                    ON id.idsup_supplier_zdb_id = su.srcurl_source_zdb_id
						                       AND su.srcurl_purpose = 'order'
						       LEFT OUTER JOIN company comp
						                    ON comp.zdb_id = id.idsup_supplier_zdb_id
						       LEFT OUTER JOIN lab l
						                    ON l.zdb_id = id.idsup_supplier_zdb_id
						WHERE  id.idsup_data_zdb_id =  :OID
			""";

		return HibernateUtil.currentSession().createSQLQuery(sql)
			.setParameter("OID", zdbID)
			.setResultTransformer(new BasicTransformerAdapter() {
				@Override
				public Object transformTuple(Object[] tuple, String[] aliases) {
					OrganizationLink organinzationLink = new OrganizationLink();
					organinzationLink.setSupplierZdbId(tuple[0].toString());
					if (tuple[1] != null) {
						organinzationLink.setSourceUrl(tuple[1].toString());
					}
					if (tuple[2] != null) {
						organinzationLink.setUrlDisplayText(tuple[2].toString());
					}
					if (tuple[3] != null) {
						organinzationLink.setAccessionNumber(tuple[3].toString());
					}
					if (tuple[4] != null) {
						organinzationLink.setCompanyName(tuple[4].toString());
					}
					if (tuple[5] != null) {
						organinzationLink.setLabName(tuple[5].toString());
					}

					return organinzationLink;
				}
			})
			.list()
			;
	}


	@Override
	public Company getCompanyById(String zdbID) {
		return HibernateUtil.currentSession().get(Company.class, zdbID);
	}

	@Override
	public List<CompanyPresentation> getCompanyForPersonId(String zdbID) {
		String sql = """
						 select b.name,
						      b.zdb_id,
						      a.position_id,
						      c.compos_order
						    from int_person_company a,
						      company b,
						      company_position c
						    where source_id= :zdbID
						    and target_id=b.zdb_id
						    and a.position_id=c.compos_pk_id
						    order by c.compos_order desc;
			""";
		List<Tuple> tupleList = HibernateUtil.currentSession().createNativeQuery(sql, Tuple.class)
			.setParameter("zdbID", zdbID)
			.list();

		return tupleList.stream()
			.map(tuple -> {
				CompanyPresentation companyPresentation = new CompanyPresentation();
				companyPresentation.setName(tuple.get(0).toString());
				companyPresentation.setZdbID(tuple.get(1).toString());
				companyPresentation.setPosition(Integer.parseInt(tuple.get(2).toString()));
				companyPresentation.setOrder(tuple.get(3).toString());
				companyPresentation.setShowPosition(false);
				return companyPresentation;
			}).collect(toList());
	}

	@Override
	public List<LabPresentation> getLabsForPerson(String zdbID) {
		String sql = """
						 select b.name,
						      b.zdb_id,
						      a.position_id,
						      c.labpos_order
						        from int_person_lab a,
						      lab b,
						      lab_position c
						        where source_id= :zdbID
						        and target_id=b.zdb_id
						        and a.position_id=c.labpos_pk_id
						        order by c.labpos_order desc, b.name
			""";
		List<Tuple> tupleList = HibernateUtil.currentSession().createNativeQuery(sql, Tuple.class)
			.setParameter("zdbID", zdbID)
			.list();
		return tupleList.stream()
			.map(tuple -> {
				LabPresentation labPresentation = new LabPresentation();
				labPresentation.setName(tuple.get(0).toString());
				labPresentation.setZdbID(tuple.get(1).toString());
				if (tuple.get(2) != null) {
					labPresentation.setPosition(Integer.parseInt(tuple.get(2).toString()));
				}
				labPresentation.setOrder(tuple.get(3).toString());
				return labPresentation;
			}).collect(toList());
	}

	@Override
	public List<PersonMemberPresentation> getLabMembers(final String zdbID) {
		String sql = """
						select b.last_name || ', ' ||b.first_name , b.zdb_id, a.position_id, c.labpos_order, c.labpos_position
						 from int_person_lab a, person b, lab_position c
						 where source_id=b.zdb_id and target_id=:zdbID
						 and a.position_id = c.labpos_pk_id
						 order by c.labpos_order,b.last_name, b.first_name
			""";
		List<Tuple> tupleList = HibernateUtil.currentSession().createNativeQuery(sql, Tuple.class)
			.setParameter("zdbID", zdbID)
			.list();
		return tupleList.stream()
			.map(tuple -> {
				PersonMemberPresentation personMemberPresentation = new PersonMemberPresentation();
				personMemberPresentation.setName(tuple.get(0).toString());
				personMemberPresentation.setPersonZdbID(tuple.get(1).toString());
				personMemberPresentation.setPosition(Integer.parseInt(tuple.get(2).toString()));
				personMemberPresentation.setOrder(Integer.parseInt(tuple.get(3).toString()));
				personMemberPresentation.setPositionString(tuple.get(4).toString());
				personMemberPresentation.setOrganizationZdbID(zdbID);
				return personMemberPresentation;
			}).collect(toList());
	}

	@Override
	public List<Publication> getPublicationsForLab(String zdbID) {
		String hql = """
			select distinct pub , pub.publicationDate, lower(pub.authors)
			 from Person pers
			 join pers.publications pub
			 join pers.labs l
			 join fetch pub.journal
			  where l.zdbID = :zdbID
			  order by pub.publicationDate desc, lower(pub.authors)
			""";
		Query<Tuple> query = currentSession().createQuery(hql, Tuple.class);
		query.setParameter("zdbID", zdbID);
		return query.getResultList().stream().map(tuple -> (Publication) tuple.get(0)).collect(toList());
	}

	@Override
	public List<PersonMemberPresentation> getCompanyMembers(final String zdbID) {
		String sql = """
			     select
						        b.last_name || ', ' ||b.first_name ,
						        b.zdb_id,
						        a.position_id,
						        c.compos_order,
						        c.compos_position
						    from
						        int_person_company a,
						        person b,
						        company_position c
						    where
						        source_id=b.zdb_id
						        and target_id= :zdbID
						        and a.position_id = c.compos_pk_id
						    order by
						        c.compos_position,
						        b.last_name , b.first_name
			""";
		List<Tuple> tupleList = HibernateUtil.currentSession().createNativeQuery(sql, Tuple.class)
			.setParameter("zdbID", zdbID)
			.list();
		return tupleList.stream()
			.map(tuple -> {
				PersonMemberPresentation personMemberPresentation = new PersonMemberPresentation();
				personMemberPresentation.setName(tuple.get(0).toString());
				personMemberPresentation.setPersonZdbID(tuple.get(1).toString());
				personMemberPresentation.setPosition(Integer.parseInt(tuple.get(2).toString()));
				personMemberPresentation.setOrder(Integer.parseInt(tuple.get(3).toString()));
				personMemberPresentation.setPositionString(tuple.get(4).toString());
				personMemberPresentation.setOrganizationZdbID(zdbID);
				return personMemberPresentation;
			}).collect(toList());
	}

	@Override
	public List<Publication> getPublicationsForCompany(String zdbID) {
		String hql = """
			    select distinct pub , pub.publicationDate, lower(pub.authors)
			    from Person pers
			    join pers.publications pub
			    join pers.companies c
				where c.zdbID = :zdbID
				order by pub.publicationDate desc, lower(pub.authors)
			""";
		Query<Tuple> query = currentSession().createQuery(hql, Tuple.class);
		query.setParameter("zdbID", zdbID);
		return query.stream().map(tuple -> (Publication) tuple.get(0)).collect(toList());
	}


	@Override
	public int removeMemberFromOrganization(String personZdbID, String organizationZdbID) {
		String sql;
		if (organizationZdbID.startsWith("ZDB-LAB")) {
			sql = " delete from int_person_lab  " +
				" where source_id = :personZdbID and target_id = :organizationZdbID ";
		} else if (organizationZdbID.startsWith("ZDB-COMPANY")) {
			sql = " delete from int_person_company  " +
				" where source_id = :personZdbID and target_id = :organizationZdbID ";
		} else {
			logger.error("Not a valid organization to remove member from: " + organizationZdbID);
			return 0;
		}
		return HibernateUtil.currentSession().createSQLQuery(sql)
			.setParameter("personZdbID", personZdbID.toUpperCase())
			.setParameter("organizationZdbID", organizationZdbID.toUpperCase())
			.executeUpdate();
	}

	@Override
	public List<PersonLookupEntry> getPersonNamesForString(String lookupString) {
		String hql = """ 
			select p FROM Person p
			where lower(p.fullName) like :lookupString
			or lower(p.firstName) like :lookupString
			order by p.fullName asc, p.zdbID desc
			""";
		Query<Tuple> query = currentSession().createQuery(hql, Tuple.class);
		query.setParameter("lookupString", lookupString.toLowerCase() + "%");
		return query.getResultList().stream()
			.map(tuple -> {
				Person p = (Person) tuple.get(0);
				PersonLookupEntry personMemberPresentation = new PersonLookupEntry();
				personMemberPresentation.setId(p.getZdbID());
				personMemberPresentation.setLabel(p.getFullName());
				personMemberPresentation.setValue(p.getFullName());
				return personMemberPresentation;

			}).collect(toList());
	}

	@Override
	public int addCompanyMember(String personZdbID, String organizationZdbID, Integer position) {
		String sql = "insert into int_person_company (source_id,target_id,position_id) " +
			" values (:personZdbID,:companyZdbID,:positionID)  ";
		logger.debug("personZdbID: " + personZdbID);
		logger.debug("organizationZdbID: " + organizationZdbID);
		logger.debug("positionID: " + position);
		return HibernateUtil.currentSession().createSQLQuery(sql)
			.setParameter("personZdbID", personZdbID)
			.setParameter("companyZdbID", organizationZdbID)
			.setParameter("positionID", position)
			.executeUpdate();
	}

	@Override
	public int addLabMember(String personZdbID, String organizationZdbID, Integer positionID) {
		String sql = "insert into int_person_lab (source_id,target_id,position_id) " +
			" values (:personZdbID,:labZdbID,:positionID)  ";
		logger.debug("person: " + personZdbID);
		logger.debug("lab: " + organizationZdbID);
		logger.debug("positionID: " + positionID);
		return HibernateUtil.currentSession().createSQLQuery(sql)
			.setParameter("personZdbID", personZdbID)
			.setParameter("labZdbID", organizationZdbID)
			.setParameter("positionID", positionID)
			.executeUpdate();
	}

	public int changeLabPosition(String personZdbID, String organizationZdbID, Integer positionID) {
		String sql = "update int_person_lab " +
			" set source_id = :personZdbID, target_id = :labZdbID, position_id = :positionID " +
			" where source_id = :personZdbID and target_id = :labZdbID";
		logger.debug("person: " + personZdbID);
		logger.debug("lab: " + organizationZdbID);
		logger.debug("positionID: " + positionID);
		return HibernateUtil.currentSession().createSQLQuery(sql)
			.setParameter("personZdbID", personZdbID)
			.setParameter("labZdbID", organizationZdbID)
			.setParameter("positionID", positionID)
			.executeUpdate();
	}

	@Override
	public List<OrganizationPosition> getLabPositions() {
		String sql = """
			select labpos_position,labpos_pk_id,labpos_order
			from lab_position
			order by labpos_order
			""";
		NativeQuery<Tuple> query = currentSession()
			.createNativeQuery(sql, Tuple.class);
		return query.getResultList().stream()
			.map(tuple -> {
				OrganizationPosition organizationPosition = new OrganizationPosition();
				organizationPosition.setName(tuple.get(0).toString());
				organizationPosition.setId(Integer.parseInt(tuple.get(1).toString()));
				return organizationPosition;
			}).collect(toList());
	}

	@Override
	public List<OrganizationPosition> getCompanyPositions() {
		String sql = """
			select compos_position,compos_pk_id,compos_order
			from company_position
			order by compos_order
			""";
		NativeQuery<Tuple> query = currentSession()
			.createNativeQuery(sql, Tuple.class);
		return query.getResultList().stream()
			.map(tuple -> {
				OrganizationPosition organizationPosition = new OrganizationPosition();
				organizationPosition.setName(tuple.get(0).toString());
				organizationPosition.setId(Integer.parseInt(tuple.get(1).toString()));
				return organizationPosition;
			}).collect(toList());
	}

	public int changeCompanyPosition(String personZdbID, String organizationZdbID, Integer positionID) {
		String sql = "update int_person_company " +
			" set source_id = :personZdbID, target_id = :companyZdbID, position_id = :positionID " +
			" where source_id = :personZdbID and target_id = :companyZdbID";
		logger.debug("person: " + personZdbID);
		logger.debug("company: " + organizationZdbID);
		logger.debug("positionID: " + positionID);
		return HibernateUtil.currentSession().createSQLQuery(sql)
			.setParameter("personZdbID", personZdbID)
			.setParameter("companyZdbID", organizationZdbID)
			.setParameter("positionID", positionID)
			.executeUpdate();
	}

	@Override
	public int removeLabMember(String personZdbID, String organizationZdbID) {
		String sql = "delete from int_person_lab where source_id = :personZdbID and target_id = :organizationID ";
		return HibernateUtil.currentSession().createSQLQuery(sql)
			.setParameter("personZdbID", personZdbID)
			.setParameter("organizationID", organizationZdbID)
			.executeUpdate();
	}

	@Override
	public int removeCompanyMember(String personZdbID, String organizationZdbID) {
		String sql = "delete from int_person_company where source_id = :personZdbID and target_id = :organizationID ";
		return HibernateUtil.currentSession().createSQLQuery(sql)
			.setParameter("personZdbID", personZdbID)
			.setParameter("organizationID", organizationZdbID)
			.executeUpdate();
	}

	@Override
	public Organization getOrganizationByZdbID(String orgZdbID) {
		if (null == orgZdbID) {
			return null;
		}
		return HibernateUtil.currentSession().get(Organization.class, orgZdbID);
	}

	@Override
	public List<Lab> getLabs() {
		return HibernateUtil.currentSession().createQuery("from Lab order by name", Lab.class)
			.getResultList();
	}

	@Override
	public List<Company> getCompanies() {
		return HibernateUtil.currentSession().createQuery("from Company order by name", Company.class)
			.getResultList();
	}


	@Override
	public PaginationResult<Company> searchCompanies(CompanySearchBean searchBean) {

		Criteria criteria = HibernateUtil.currentSession()
			.createCriteria(Company.class).addOrder(Order.asc("name"));
		if (StringUtils.isNotEmpty(searchBean.getName())) {
			criteria = addTokenizedLikeRestriction("name", searchBean.getName(), criteria);
		}
		if (StringUtils.isNotEmpty(searchBean.getAddress())) {
			criteria = addTokenizedLikeRestriction("address", searchBean.getAddress(), criteria);
		}

		if (StringUtils.isNotEmpty(searchBean.getContains())) {
			String containsType = searchBean.getContainsType();
			switch (containsType) {
				case "bio" -> criteria = addTokenizedLikeRestriction("bio", searchBean.getContains(), criteria);
				case "email" -> criteria.add(Restrictions.ilike("email", "%" + searchBean.getContains() + "%"));
				case "url" -> criteria.add(Restrictions.ilike("url", "%" + searchBean.getContains() + "%"));
				case "fax" -> criteria.add(Restrictions.ilike("fax", "%" + searchBean.getContains() + "%"));
				case "phone" -> criteria.add(Restrictions.ilike("phone", "%" + searchBean.getContains() + "%"));
				case "zdb_id" -> criteria.add(Restrictions.ilike("zdbID", "%" + searchBean.getContains() + "%"));
			}
		}

//        return criteria.list();
		PaginationResult<Company> paginationResult = PaginationResultFactory.createResultFromScrollableResultAndClose(
			searchBean.getFirstRecordOnPage() - 1, searchBean.getLastRecordOnPage(), criteria.scroll());
		paginationResult.setStart(searchBean.getFirstRecord());

		return paginationResult;
	}

	@Override
	public PaginationResult<Lab> searchLabs(LabSearchBean searchBean) {

		Criteria criteria = HibernateUtil.currentSession()
			.createCriteria(Lab.class).addOrder(Order.asc("name"));
		if (StringUtils.isNotEmpty(searchBean.getName())) {
			criteria = addTokenizedLikeRestriction("name", searchBean.getName(), criteria);
		}
		if (StringUtils.isNotEmpty(searchBean.getAddress())) {
			criteria = addTokenizedLikeRestriction("address", searchBean.getAddress(), criteria);
		}

		if (StringUtils.isNotEmpty(searchBean.getContains())) {
			String containsType = searchBean.getContainsType();
			switch (containsType) {
				case "bio" -> criteria = addTokenizedLikeRestriction("bio", searchBean.getContains(), criteria);
				case "email" -> criteria.add(Restrictions.ilike("email", "%" + searchBean.getContains() + "%"));
				case "url" -> criteria.add(Restrictions.ilike("url", "%" + searchBean.getContains() + "%"));
				case "fax" -> criteria.add(Restrictions.ilike("fax", "%" + searchBean.getContains() + "%"));
				case "phone" -> criteria.add(Restrictions.ilike("phone", "%" + searchBean.getContains() + "%"));
				case "zdb_id" -> criteria.add(Restrictions.ilike("zdbID", "%" + searchBean.getContains() + "%"));
			}
		}

		PaginationResult<Lab> paginationResult = PaginationResultFactory.createResultFromScrollableResultAndClose(
			searchBean.getFirstRecordOnPage() - 1, searchBean.getLastRecordOnPage(), criteria.scroll());
		paginationResult.setStart(searchBean.getFirstRecord());

		return paginationResult;
	}

	@Override
	public List<Person> getPersonByLastNameStartsWith(String lastNameStartsWith) {
		if (lastNameStartsWith == null) {
			String hql = "from Person where lastName is null order by zdbID";
			return HibernateUtil.currentSession().createQuery(hql, Person.class)
				.list();
		}
		String hql = "from Person where lastName like :lastName order by lastName, firstName";
		return HibernateUtil.currentSession().createQuery(hql, Person.class)
			.setParameter("lastName", lastNameStartsWith + "%")
			.list();
	}

	@Override
	public List<Person> getPersonByLastNameStartsWithAndFirstNameStartsWith(String lastNameStartsWith, String firstNameStartsWith) {
		String hql = """
			from Person
			where lower(lastName) like :lastName
			AND   lower(firstName) like :firstName
			ORDER BY lastName, firstName
			""";
		Query<Person> query = currentSession().createQuery(hql, Person.class);
		query.setParameter("lastName", lastNameStartsWith.toLowerCase() + "%");
		query.setParameter("firstName", firstNameStartsWith.toLowerCase() + "%");
		return query.getResultList();
	}

	@Override
	public List<Person> getPersonByLastNameEqualsAndFirstNameStartsWith(String lastName, String firstNameStartsWith) {
		String hql = """
			from Person
			where lower(lastName) = :lastName
			AND   lower(firstName) like :firstName
			ORDER BY lastName, firstName
			""";
		Query<Person> query = currentSession().createQuery(hql, Person.class);
		query.setParameter("lastName", lastName.toLowerCase());
		query.setParameter("firstName", firstNameStartsWith.toLowerCase() + "%");
		return query.getResultList();
	}


	@Override
	public boolean isOrganizationPersonExist(String personZdbID, String organizationZdbID) {
		String queryString = """
			select ipc.source_id, ipc.target_id from int_person_company ipc
			where ipc.source_id=:personZdbID and ipc.target_id= :organizationZdbID
			union
			select ipl.source_id, ipl.target_id from int_person_lab ipl
			where ipl.source_id=:personZdbID  and ipl.target_id= :organizationZdbID
			""";
		return HibernateUtil.currentSession().createNativeQuery(queryString)
			.setParameter("personZdbID", personZdbID)
			.setParameter("organizationZdbID", organizationZdbID)
			.list()
			.size() > 0
			;
	}

	@Override
	public Address getAddress(Long addressId) {
		return HibernateUtil.currentSession().get(Address.class, addressId);
	}

	@Override
	public PaginationResult<Person> searchPeople(PersonSearchBean searchBean) {

		Criteria criteria = HibernateUtil.currentSession()
			.createCriteria(Person.class)
			.add(Restrictions.eq("hidden", false))
			.addOrder(Order.asc("lastName"))
			.addOrder(Order.asc("firstName"));
		if (StringUtils.isNotEmpty(searchBean.getName())) {
			criteria = addTokenizedLikeRestriction("fullName", searchBean.getName(), criteria);
		}
		if (StringUtils.isNotEmpty(searchBean.getAddress())) {
			criteria = addTokenizedLikeRestriction("address", searchBean.getAddress(), criteria);
		}

		if (StringUtils.isNotEmpty(searchBean.getContains())) {
			String containsType = searchBean.getContainsType();
			if (containsType.equals("bio")) {
				criteria = addTokenizedLikeRestriction("personalBio", searchBean.getContains(), criteria);
			} else if (containsType.equals("email")) {
				criteria = addTokenizedLikeRestriction("email", searchBean.getContains(), criteria);
			} else if (containsType.equals("url")) {
				criteria.add(Restrictions.ilike("url", "%" + searchBean.getContains() + "%"));
			} else if (containsType.equals("fax")) {
				criteria.add(Restrictions.ilike("fax", "%" + searchBean.getContains() + "%"));
			} else if (containsType.equals("phone")) {
				criteria.add(Restrictions.ilike("phone", "%" + searchBean.getContains() + "%"));
			} else if (containsType.equals("zdb_id")) {
				criteria.add(Restrictions.ilike("zdbID", "%" + searchBean.getContains() + "%"));
			}
		}
//        return criteria.list();
		PaginationResult<Person> paginationResult = PaginationResultFactory.createResultFromScrollableResultAndClose(
			searchBean.getFirstRecordOnPage() - 1, searchBean.getLastRecordOnPage(), criteria.scroll());
		paginationResult.setStart(searchBean.getFirstRecord());

		return paginationResult;
	}


	/**
	 * Doing multi-word searching is something that's useful in all of these searches
	 * <p>
	 * Returning the criteria doesn't really do anything, but it feels like it's a little
	 * more immediately obvious what's going on if I do it that way.
	 */
	private Criteria addTokenizedLikeRestriction(String fieldName, String queryString, Criteria criteria) {
		if (fieldName != null && queryString != null && criteria != null) {
			for (String queryTerm : queryString.split(" ")) {
				criteria.add(Restrictions.ilike(fieldName, "%" + queryTerm + "%"));
			}
		}

		return criteria;
	}

	public List<String> getDistributionList() {
		String hql = """
			select distinct email from person
							where on_dist_list='t'
							and email is not null
							and email != ''
			""";
		List<Tuple> resultList = currentSession().createNativeQuery(hql, Tuple.class).getResultList();
		return resultList.stream().map(o -> (String) o.get(0)).collect(toList());
	}

	public List<String> getPiDistributionList() {
		String hql = """
			select distinct email from person, int_person_lab, lab_position
							where zdb_id = source_id
							and position_id = labpos_pk_id
							and labpos_position in ('PI/Director', 'Co-PI/Senior Scientist')
							and email is not null
							and email != ''
							and on_dist_list='t'
										""";
		List<Tuple> resultList = currentSession().createNativeQuery(hql, Tuple.class).getResultList();
		return resultList.stream().map(o -> (String) o.get(0)).collect(toList());
	}

	public List<String> getUsaDistributionList() {
		String hql = """
			select distinct email from person
							where on_dist_list='t'
							and upper(address) like '%USA%'
							and email is not null
							and email != ''
							""";
		List<Tuple> resultList = currentSession().createNativeQuery(hql, Tuple.class).getResultList();
		return resultList.stream().map(o -> (String) o.get(0)).collect(toList());
	}

	public List<Person> getCurators() {
		Query<Person> query = currentSession()
			.createQuery("from Person where accountInfo.curator = true ORDER BY fullName", Person.class);
		return query.list();
	}

	public List<Person> getStudents() {
		Query<Person> query = currentSession()
			.createQuery("from Person where accountInfo.student = true ORDER BY fullName", Person.class);
		return query.list();
	}

	@Override
	public List<Person> getPersonByLastNameEquals(String lastName) {
		Query<Person> query = currentSession()
			.createQuery("from Person where lastName = :lastName ORDER BY fullName, firstName", Person.class);
		query.setParameter("lastName", lastName);
		return query.list();
	}

	@Override
	public boolean emailExists(String email) {
		Session session = HibernateUtil.currentSession();
		Query<Person> query = session.createQuery("from Person where email = :email", Person.class);
		query.setParameter("email", email);
		List<Person> persons = query.list();
		return persons.size() >= 1;
	}
}
