package com.stupid-genius.utils

import com.google.inject.Injector;
import java.util.Collection;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;
import org.jboss.seam.Component;

/**
 *  Operations over a class of Entities
 *
 *  TODO: need better error handling
 *	IDEA: Can I use the metamodel to create queries for all attributes ahead of time and call them via reflection?
 *
 * Created by allen on 2/29/16.
 */
public class Entity<E>{
	final Root root;
	final EntityType type_;
	final Class clazz;
	TypedQuery<E> listQuery = null;
	TypedQuery<E> singleQuery = null;
	CriteriaQuery countQuery = null;
	private EntityManager entityManager;
	private TransactionHandler transactionHandler;

	public Entity(Class entityClass) {
		this(entityClass, null);
	}

	public Entity(Class entityClass, EntityManager entityManager){
		if(entityManager == null) {
			Injector injector = (Injector) Component.getInstance("guiceInjector");
			this.entityManager = injector.getInstance(EntityManager.class);
		}
		else {
			this.entityManager = entityManager;
		}
		this.transactionHandler = new TransactionHandler().withEntityManager(this.entityManager);
		CriteriaBuilder criteriaBuilder = this.entityManager.getCriteriaBuilder();
		clazz = entityClass;
		CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(clazz);
		root = criteriaQuery.from(clazz);
		criteriaQuery.select(root);
		listQuery = this.entityManager.createQuery(criteriaQuery);

		countQuery = criteriaBuilder.createQuery(Long.class);
		countQuery.from(clazz);
		countQuery.select(criteriaBuilder.count(root));

		type_ = root.getModel();
		Class pkType = type_.getIdType().getJavaType();
		SingularAttribute pk = type_.getId(pkType);
		criteriaQuery.where(criteriaBuilder.equal(root.get(pk), criteriaBuilder.parameter(pkType, "id")));
		singleQuery = this.entityManager.createQuery(criteriaQuery);
	}

	public Entity<E> withTransactionHandler(final TransactionHandler transactionHandler) {
		this.transactionHandler = transactionHandler.withEntityManager(this.entityManager);
		return this;
	}

	public List<E> getList(){
		return listQuery.getResultList();
	}

	// TODO add vararg version to handle compound pk
	public E getSingle(Object id){
		singleQuery.setParameter("id", id);
		return singleQuery.getSingleResult();
	}

	// TODO add support for multi attribute
	public List<E> getByAttribute(String attributeName, Object attributeValue){
		return getAttributeQuery(attributeName, attributeValue).getResultList();
	}

	public E getSingleByAttribute(String attributeName, Object attributeValue){
		return getAttributeQuery(attributeName, attributeValue).getSingleResult();
	}

	private TypedQuery<E> getAttributeQuery(String attributeName, Object attributeValue){
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery criteriaQuery = criteriaBuilder.createQuery(clazz);
		criteriaQuery.from(clazz);
		criteriaQuery.select(root);
		SingularAttribute attribute = type_.getSingularAttribute(attributeName);
		criteriaQuery.where(criteriaBuilder.equal(root.get(attribute), attributeValue));
		return entityManager.createQuery(criteriaQuery);
	}

	public Long count(){
		return getCountQuery().getSingleResult();
	}

	public Long count(String attributeName, Object attributeValue){
		return getCountQuery(attributeName, attributeValue).getSingleResult();
	}

	private TypedQuery<Long> getCountQuery(){
		return entityManager.createQuery(countQuery);
	}
	private TypedQuery<Long> getCountQuery(String attributeName, Object attributeValue){
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		SingularAttribute attribute = type_.getSingularAttribute(attributeName);
		countQuery.where(criteriaBuilder.equal(root.get(attribute), attributeValue));
		return entityManager.createQuery(countQuery);
	}

	// TODO WIP
	public void removeSingle(Object id){
		EntityManager entityManager = this.transactionHandler.startTransaction();
		E e = getSingle(id);
		entityManager.remove(e);
		this.transactionHandler.endTransaction();
	}

	// TODO WIP
	public void removeByAttribute(String attributeName, Object attributeValue){
		EntityManager entityManager = this.transactionHandler.startTransaction();
		List<E> selection = getByAttribute(attributeName, attributeValue);
		for(E e : selection){
			entityManager.remove(e);
		}
		this.transactionHandler.endTransaction();
	}

	public void putList(Collection<E> eList){
		EntityManager entityManager = this.transactionHandler.startTransaction();
		for(E e : eList){
			if(entityManager.contains(e)){
				entityManager.merge(e);
			}
			else{
				entityManager.persist(e);
			}
		}
		this.transactionHandler.endTransaction();
	}

	public void putSingle(E e){
		EntityManager entityManager = this.transactionHandler.startTransaction();
		if(entityManager.contains(e)){
			entityManager.merge(e);
		}
		else{
			entityManager.persist(e);
		}
		this.transactionHandler.endTransaction();
	}

}
