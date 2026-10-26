import { Kind, print, visit } from 'graphql';
import { callProjectAssistance } from './client.js';

function getLeafTypeName(typeObj) {
  let t = typeObj;
  while (t.kind === Kind.NON_NULL_TYPE || t.kind === Kind.LIST_TYPE) {
    t = t.type;
  }
  return t.name.value;
}

function buildForwardedQuery(info, operation, args) {
  const parentType = operation === 'mutation' ? info.schema.getMutationType() : info.schema.getQueryType();
  const fieldDef = parentType?.getFields?.()[info.fieldName];
  const argTypes = new Map();
  if (fieldDef) {
    for (const arg of fieldDef.args) {
      argTypes.set(arg.name, arg.type.toString());
    }
  }

  const usedArgs = Object.keys(args ?? {});
  const varDefs = usedArgs.map((name) => `$${name}: ${argTypes.get(name) ?? 'String'}`);
  const varRefs = usedArgs.map((name) => `${name}: $${name}`);

  const fieldNode = info.fieldNodes[0];
  const selectionSet = fieldNode.selectionSet
    ? visit(fieldNode.selectionSet, {
        Field(node) {
          if (node.name.value === '__typename') {
            return null;
          }
          return undefined;
        }
      })
    : null;

  const selection = selectionSet ? ` ${print(selectionSet)}` : '';
  const fieldCall = varRefs.length ? `${info.fieldName}(${varRefs.join(', ')})` : info.fieldName;
  return `${operation}${varDefs.length ? `(${varDefs.join(', ')})` : ''} { ${fieldCall}${selection} }`;
}

function forward(operation) {
  return async (_, args, contextValue, info) => {
    const query = buildForwardedQuery(info, operation, args);
    const data = await callProjectAssistance(query, args, contextValue.token);
    return data ? data[info.fieldName] : null;
  };
}

const queryOps = [
  'getMyProjects',
  'getDashboardPM',
  'getNotificationsByType',
  'listAttendanceAlerts',
  'listVacations',
  'getVacationBalance',
  'getJustificationDetail',
  'getAlertDetail',
  'listShiftTemplates',
  'listholidays',
  'getHolidayExceptionsForUser',
  'getProjectById',
  'listScheduleOverrides',
  'getMyVacationEligibility',
  'exportAttendance',
  'getGlobalCollections',
  'getProjectMembers',
  'listTeamAbsences',
  'getEffectiveSchedule',
  'listTeamAttendance',
  'getAlertsByUser',
  'getAllAlerts',
  'listJustificationsPM',
  'getAllProjects',
  'getAdminSetting',
  'hasApprovedVacation',
  'isHolidayForUser',
  'listTeamMembers',
  'listProjectManagers',
  'getCollectionsForProject'
];

const mutationOps = [
  'createShiftTemplate',
  'updateProject',
  'createProject',
  'assignSchedule',
  'bulkcreateholidays',
  'deleteCollectionItem',
  'unlinkProjectFromCollection',
  'approveAlert',
  'deleteShiftTemplate',
  'updateAdminSetting',
  'rejectVacation',
  'requestObservation',
  'addProjectMember',
  'deleteProject',
  'createHoliday',
  'approveVacation',
  'requestVacation',
  'approveJustification',
  'deleteProjectMember',
  'deleteSchedule',
  'updateProjectMember',
  'deleteCollection',
  'addDatesToCollection',
  'createAlert',
  'cancelVacation',
  'deleteAllHolidays',
  'importHolidaysFromCsv',
  'cancelAlert',
  'createNotification',
  'createCollection',
  'rejectJustification',
  'linkProjectToCollection',
  'deleteHoliday'
];

export const resolvers = {
  Query: Object.fromEntries(queryOps.map((op) => [op, forward('query')])),
  Mutation: Object.fromEntries(mutationOps.map((op) => [op, forward('mutation')])),

  AttendanceOutput: {
    __resolveReference(reference) {
      return { id: reference.id };
    }
  },

  AlertOutput: {
    __resolveReference(reference) {
      return { id: reference.id };
    }
  },

  JustificationOutput: {
    __resolveReference(reference) {
      return { id: reference.id };
    }
  },

  AbsenceOutput: {
    __resolveReference(reference) {
      return { id: reference.id };
    }
  },

  UserOutput: {
    __resolveReference(reference) {
      return { id: reference.id };
    }
  },

  ProjectOutput: {
    __resolveReference(reference) {
      return { id: reference.id };
    }
  },

  ScheduleOutput: {
    __resolveReference(reference) {
      return { id: reference.id };
    }
  }
};
